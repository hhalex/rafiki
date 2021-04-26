package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import com.lion.rafiki.domain.CompanyContract.Kind
import com.lion.rafiki.domain.{Company, CompanyContract, RepoError, ValidationError, WithId}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import shapeless.tag
import shapeless.tag.@@

import java.time.Instant

case class FormSession(companyContractId: CompanyContract.Id, testForm: Form.Id, name: String, startDate: Option[Instant], endDate: Option[Instant]) {
  def withId(id: FormSession.Id) = WithId(id, this)
}

object FormSession {
  type Id = Long @@ FormSession
  val tagSerial = tag[FormSession](_: Long)

  type Create = FormSession
  type Update = WithId[Id, Create]
  type Record = Update
  type Full = Update

  implicit val formSessionIdDecoder: Decoder[Id] = Decoder[Long].map(tagSerial)
  implicit val formSessionIdEncoder: Encoder[Id] = Encoder[Long].contramap(_.asInstanceOf[Long])

  implicit val formSessionCreateDecoder: Decoder[Create] = deriveDecoder
  implicit val formSessionCreateEncoder: Encoder[Create] = deriveEncoder
  implicit val formSessionUpdateDecoder: Decoder[Update] = WithId.decoder
  implicit val formSessionFullEncoder: Encoder[Full] = WithId.encoder

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(formSession: Create): Result[Full]
    def update(formSession: Update): Result[Full]
    def get(id: Id): Result[Full]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompanyContract(companyContractId: CompanyContract.Id, pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompany(companyId: Company.Id, pageSize: Int, offset: Int): Result[List[Record]]
  }

  trait Validation[F[_]] {
    def canCreateSession(id: CompanyContract.Id, companyId: Option[Company.Id]): EitherT[F, ValidationError, Unit]
    def hasOwnership(id: FormSession.Id, companyId: Option[Company.Id]): EitherT[F, ValidationError, Full]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F], formValidation: Form.Validation[F], companyContractValidation: CompanyContract.Validation[F]) extends Validation[F] {
    val success = EitherT.rightT[F, ValidationError](())
    val contractFull = EitherT.leftT[F, Unit](ValidationError.CompanyContractFull: ValidationError)
    override def canCreateSession(id: CompanyContract.Id, companyId: Option[Company.Id]) = for {
      companyContract <- companyContractValidation.hasOwnership(id, companyId)
      _ <- companyContract.data.kind match {
        case Kind.Unlimited => success
        case Kind.OneShot => repo.listByCompanyContract(id, 0, 100)
          .leftMap[ValidationError](ValidationError.Repo)
          .flatMap {
            case Nil => success
            case _ => contractFull
          }
        case _ => contractFull
      }
    } yield ()

    override def hasOwnership(id: Id, companyId: Option[Company.Id]) = for {
      formSession <- repo.get(id).leftMap(ValidationError.Repo)
      _ <- formValidation.hasOwnership(formSession.data.testForm, companyId)
    } yield formSession
  }

  class Service[F[_] : Monad](repo: Repo[F], validation: Validation[F]) {
    type Result[T] = EitherT[F, ValidationError, T]

    def create(formSession: Create, companyId: Option[Company.Id]): Result[Full] = for {
      _ <- validation.canCreateSession(formSession.companyContractId, companyId)
      createdFormSession <- repo.create(formSession).leftMap[ValidationError](ValidationError.Repo)
    } yield createdFormSession

    def getById(formSessionId: Id, companyId: Option[Company.Id]): Result[Full] =
      validation.hasOwnership(formSessionId, companyId)

    def delete(formSessionId: Id, companyId: Option[Company.Id]): Result[Unit] = for {
      _ <- validation.hasOwnership(formSessionId, companyId)
      _ <- repo.delete(formSessionId).leftMap[ValidationError](ValidationError.Repo)
    } yield ()

    def update(formSession: Update, companyId: Option[Company.Id]): Result[Full] = for {
      _ <- validation.hasOwnership(formSession.id, companyId)
      result <- repo.update(formSession).leftMap[ValidationError](ValidationError.Repo)
    } yield result

    def listByCompany(companyId: Company.Id, pageSize: Int, offset: Int): Result[List[Record]] =
      repo.listByCompany(companyId, pageSize, offset).leftMap(ValidationError.Repo)
  }
}


