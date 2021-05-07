package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import cats.implicits.{catsSyntaxOptionId, toTraverseOps}
import com.lion.rafiki.domain.CompanyContract.Kind
import com.lion.rafiki.domain.{
  Company,
  CompanyContract,
  RepoError,
  TaggedId,
  ValidationError,
  WithId
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import org.joda.time.DateTime

import org.joda.time.format.ISODateTimeFormat
import scala.util.control.NonFatal

case class FormSession(
    formId: Form.Id,
    name: String,
    startDate: Option[DateTime],
    endDate: Option[DateTime]
) {
  import FormSession._
  def withId(id: Id) = WithId(id, this)
  val state: Either[String, State] =
    if startDate.isEmpty && endDate.isEmpty then
      Right(State.Pending)
    else if startDate.isDefined && endDate.isEmpty then
      Right(State.Started)
    else if startDate.isDefined && endDate.isDefined then
      Right(State.Finished)
    else
      Left("Broken state")
}

private trait SessionId
object FormSession extends TaggedId[SessionId] {

  type Create = FormSession
  type Update = WithId[Id, Create]
  type Record = Update
  type Full = Update

  enum State {
    case Pending, Started, Finished
  }

  import Form.{taggedIdDecoder, taggedIdEncoder}
  val dateFormatter = ISODateTimeFormat.basicDateTime()
  implicit val decodeDateTime: Decoder[DateTime] = Decoder.decodeString.emap { s =>
    try {
      Right(dateFormatter.parseDateTime(s))
    } catch {
      case NonFatal(e) => Left(e.getMessage)
    }
  }
  implicit val encodeDateTime: Encoder[DateTime] = Encoder.instance { s =>
    dateFormatter.print(s).asJson
  }
  implicit val formSessionCreateDecoder: Decoder[Create] = deriveDecoder
  implicit val formSessionCreateEncoder: Encoder[Create] = deriveEncoder
  implicit val formSessionUpdateDecoder: Decoder[Update] = WithId.decoder
  implicit val formSessionFullEncoder: Encoder[Full] = WithId.encoder

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(
        formSession: Create,
        companyContract: CompanyContract.Id
    ): Result[Full]
    def update(formSession: Update): Result[Full]
    def get(id: Id): Result[Full]
    def getByCompanyContract(
        companyContractId: CompanyContract.Id
    ): Result[List[Record]]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompanyContract(
        companyContractId: CompanyContract.Id,
        pageSize: Int,
        offset: Int
    ): Result[List[Record]]
    def listByCompany(
        companyId: Company.Id,
        pageSize: Int,
        offset: Int
    ): Result[List[Record]]
  }

  trait Validation[F[_]] {
    def canCreateSession(
        formId: Form.Id,
        companyId: Company.Id
    ): EitherT[F, ValidationError, CompanyContract.Record]
    def hasOwnership(
        id: FormSession.Id,
        companyId: Company.Id
    ): EitherT[F, ValidationError, Full]
  }

  class FromRepoValidation[F[_]: Monad](
      repo: Repo[F],
      formValidation: Form.Validation[F],
      companyContractRepo: CompanyContract.Repo[F]
  ) extends Validation[F] {
    val success = EitherT.rightT[F, ValidationError](())
    val contractFull = EitherT.leftT[F, CompanyContract.Record](
      ValidationError.CompanyContractFull: ValidationError
    )
    override def canCreateSession(formId: Form.Id, companyId: Company.Id) = {
      val checkContractIdValid = (contract: CompanyContract.Record) =>
        contract.data.kind match {
          case Kind.Unlimited => EitherT.rightT[F, ValidationError](contract)
          case Kind.OneShot =>
            repo
              .getByCompanyContract(contract.id)
              .leftMap[ValidationError](ValidationError.Repo)
              .flatMap {
                case Nil => EitherT.rightT[F, ValidationError](contract)
                case _   => contractFull
              }
          case _ => contractFull
        }
      for
        _ <- formValidation.hasOwnership(formId, companyId.some)
        contracts <- companyContractRepo
          .getByCompany(companyId)
          .leftMap(ValidationError.Repo)
        validContract <- contracts match {
          case Nil => contractFull
          case hd :: tail =>
            tail.foldLeft(checkContractIdValid(hd))((prec, record) =>
              prec.orElse(checkContractIdValid(record))
            )
        }
      yield validContract
    }

    override def hasOwnership(id: Id, companyId: Company.Id) = for
      formSession <- repo.get(id).leftMap(ValidationError.Repo)
      _ <- formValidation.hasOwnership(formSession.data.formId, companyId.some)
    yield formSession
  }

  class Service[F[_]: Monad](repo: Repo[F], validation: Validation[F], now: () => DateTime) {
    type Result[T] = EitherT[F, ValidationError, T]

    def create(
        formSession: Create,
        formId: Form.Id,
        companyId: Company.Id
    ): Result[Full] = for
      contract <- validation.canCreateSession(formId, companyId)
      createdFormSession <- repo
        .create(formSession.copy(formId = formId), contract.id)
        .leftMap[ValidationError](ValidationError.Repo)
    yield createdFormSession

    def getById(formSessionId: Id, companyId: Company.Id): Result[Full] =
      validation.hasOwnership(formSessionId, companyId)

    def getByCompanyContract(
        companyId: CompanyContract.Id
    ): Result[List[Record]] =
      repo.getByCompanyContract(companyId).leftMap(ValidationError.Repo)

    def delete(formSessionId: Id, companyId: Company.Id): Result[Unit] = for
      _ <- validation.hasOwnership(formSessionId, companyId)
      _ <- repo
        .delete(formSessionId)
        .leftMap[ValidationError](ValidationError.Repo)
    yield ()

    def update(formSession: Update, companyId: Company.Id): Result[Full] = for
      _ <- validation.hasOwnership(formSession.id, companyId)
      result <- repo
        .update(formSession)
        .leftMap[ValidationError](ValidationError.Repo)
    yield result

    def start(formSessionId: FormSession.Id, companyId: Company.Id): Result[Full] = for
      formSession <- validation.hasOwnership(formSessionId, companyId)
      state <- EitherT.fromEither(formSession.data.state).leftMap[ValidationError](_ => ValidationError.FormSessionBrokenState)
      _ <- state match
        case State.Pending => EitherT.rightT(())
        case other => EitherT.leftT[F, ValidationError](ValidationError.FormSessionCantStart(other))
      result <- repo
        .update(formSession.mapData(_.copy(startDate = now().some)))
        .leftMap[ValidationError](ValidationError.Repo)
    yield result

    def finish(formSessionId: FormSession.Id, companyId: Company.Id): Result[Full] = for
      formSession <- validation.hasOwnership(formSessionId, companyId)
      state <- EitherT.fromEither(formSession.data.state).leftMap[ValidationError](_ => ValidationError.FormSessionBrokenState)
      _ <- state match
        case State.Started => EitherT.rightT(())
        case other => EitherT.leftT[F, ValidationError](ValidationError.FormSessionCantFinish(other))
      result <- repo
        .update(formSession.mapData(_.copy(endDate = now().some)))
        .leftMap[ValidationError](ValidationError.Repo)
    yield result

    def listByCompany(
        companyId: Company.Id,
        pageSize: Int,
        offset: Int
    ): Result[List[Record]] =
      repo
        .listByCompany(companyId, pageSize, offset)
        .leftMap(ValidationError.Repo)

  }
}
