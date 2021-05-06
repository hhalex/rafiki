package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import com.lion.rafiki.domain.{Company, RepoError, TaggedId, User, ValidationError, WithId}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class FormSessionInvite[User](user: User, team: String, acceptConditions: Option[Boolean]) {
  def withId(id: FormSessionInvite.Id) = WithId(id, this)
}
trait InviteId
object FormSessionInvite extends TaggedId[InviteId] {
  import User.{taggedIdDecoder, taggedIdEncoder}
  type Create = FormSessionInvite[User.Create]
  type CreateRecord = FormSessionInvite[User.Id]
  type Update = WithId[Id, Create]
  type Record = WithId[Id, CreateRecord]
  type RecordWithEmail = WithId[Id, FormSessionInvite[String]]
  type UpdateRecord = Record
  type Full = WithId[Id, FormSessionInvite[User.Full]]

  implicit def formSessionInviteDecoder[T: Decoder]: Decoder[FormSessionInvite[T]] = deriveDecoder
  implicit def formSessionInviteEncoder[T: Encoder]: Encoder[FormSessionInvite[T]] = deriveEncoder
  implicit val formSessionInviteCreateDecoder: Decoder[Create] = deriveDecoder
  implicit val formSessionInviteCreateRecordDecoder: Decoder[CreateRecord] = deriveDecoder
  implicit val formSessionInviteUpdateDecoder: Decoder[Update] = WithId.decoder
  implicit val formSessionInviteUpdateRecordDecoder: Decoder[Record] = WithId.decoder
  implicit val formSessionInviteUpdateRecordEmailDecoder: Decoder[RecordWithEmail] = WithId.decoder
  implicit val formSessionInviteRecordEncoder: Encoder[Record] = WithId.encoder
  implicit val formSessionInviteRecordEmailEncoder: Encoder[RecordWithEmail] = WithId.encoder
  implicit val formSessionInviteFullEncoder: Encoder[Full] = WithId.encoder

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(formSessionInvite: CreateRecord, formSessionId: FormSession.Id): Result[Record]
    def update(formSessionInvite: UpdateRecord): Result[Record]
    def get(id: Id): Result[(FormSession.Id, RecordWithEmail)]
    def getByFormSession(id: FormSession.Id): Result[List[RecordWithEmail]]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[(FormSession.Id, FormSessionInvite.RecordWithEmail)]]
    def listByFormSession(formSessionId: FormSession.Id, pageSize: Int, offset: Int): Result[List[RecordWithEmail]]
  }

  trait Validation[F[_]] {
    def canCreateSessionInvite(formId: FormSession.Id, companyId: Company.Id): EitherT[F, ValidationError, FormSession.Full]
    def hasOwnership(id: FormSessionInvite.Id, companyId: Company.Id): EitherT[F, ValidationError, RecordWithEmail]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F], formSessionValidation: FormSession.Validation[F]) extends Validation[F] {
    val success = EitherT.rightT[F, ValidationError](())
    val contractFull = EitherT.leftT[F, Unit](ValidationError.CompanyContractFull: ValidationError)
    override def canCreateSessionInvite(formId: FormSession.Id, companyId: Company.Id) =
      formSessionValidation.hasOwnership(formId, companyId)

    override def hasOwnership(id: Id, companyId: Company.Id) = for
      formSessionInvite <- repo.get(id).leftMap(ValidationError.Repo)
      _ <- formSessionValidation.hasOwnership(formSessionInvite._1, companyId)
    yield formSessionInvite._2
  }

  class Service[F[_] : Monad](repo: Repo[F], validation: Validation[F], formSessionValidation: FormSession.Validation[F], userService: User.Service[F]) {
    type Result[T] = EitherT[F, ValidationError, T]

    def create(formSessionInvite: FormSessionInvite[String], formSessionId: FormSession.Id, companyId: Company.Id): Result[Full] = for
      _ <- validation.canCreateSessionInvite(formSessionId, companyId)
      user <- userService.getByName(formSessionInvite.user)
        .orElse(userService.create(User[String](None, None, formSessionInvite.user, "pass")))
      createdFormSessionInvite <- repo.create(formSessionInvite.copy(user = user.id), formSessionId)
        .leftMap[ValidationError](ValidationError.Repo)
    yield createdFormSessionInvite.mapData(_.copy(user = user))

    def getById(formSessionInviteId: Id, companyId: Company.Id): Result[RecordWithEmail] =
      validation.hasOwnership(formSessionInviteId, companyId)

    def delete(formSessionInviteId: Id, companyId: Company.Id): Result[Unit] = for
      _ <- validation.hasOwnership(formSessionInviteId, companyId)
      _ <- repo.delete(formSessionInviteId).leftMap[ValidationError](ValidationError.Repo)
    yield ()

    def update(formSessionInvite: RecordWithEmail, companyId: Company.Id): Result[Record] = for
      _ <- validation.hasOwnership(formSessionInvite.id, companyId)
      user <- userService.getByName(formSessionInvite.data.user)
        .orElse(userService.create(User[String](None, None, formSessionInvite.data.user, "pass")))
      result <- repo.update(formSessionInvite.mapData(_.copy(user = user.id)))
        .leftMap[ValidationError](ValidationError.Repo)
    yield result

    def listByFormSession(formSessionId: FormSession.Id, companyId: Company.Id, pageSize: Int, offset: Int): Result[List[RecordWithEmail]] = for
      _ <- formSessionValidation.hasOwnership(formSessionId, companyId)
      list <- repo.listByFormSession(formSessionId, pageSize, offset).leftMap[ValidationError](ValidationError.Repo)
    yield list

    def getByFormSession(formSessionId: FormSession.Id, companyId: Company.Id): Result[List[RecordWithEmail]] = for
      _ <- formSessionValidation.hasOwnership(formSessionId, companyId)
      list <- repo.getByFormSession(formSessionId).leftMap[ValidationError](ValidationError.Repo)
    yield list
  }
}


