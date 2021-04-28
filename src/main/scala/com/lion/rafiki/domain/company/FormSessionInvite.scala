package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import cats.implicits.catsSyntaxOptionId
import com.lion.rafiki.domain.{Company, RepoError, TaggedId, User, ValidationError, WithId}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class FormSessionInvite[User](formSession: FormSession.Id, user: User, acceptConditions: Option[Boolean]) {
  def withId(id: FormSessionInvite.Id) = WithId(id, this)
}

object FormSessionInvite extends TaggedId[FormSessionInvite[_]] {

  type Create = FormSessionInvite[User.Create]
  type CreateRecord = FormSessionInvite[User.Id]
  type Update = WithId[Id, Create]
  type Record = WithId[Id, CreateRecord]
  type UpdateRecord = Record
  type Full = WithId[Id, FormSessionInvite[User.Full]]

  implicit def formSessionInviteDecoder[T: Decoder]: Decoder[FormSessionInvite[T]] = deriveDecoder
  implicit def formSessionInviteEncoder[T: Encoder]: Encoder[FormSessionInvite[T]] = deriveEncoder
  implicit val formSessionInviteCreateDecoder: Decoder[Create] = deriveDecoder
  implicit val formSessionInviteCreateRecordDecoder: Decoder[CreateRecord] = deriveDecoder
  implicit val formSessionInviteUpdateDecoder: Decoder[Update] = WithId.decoder
  implicit val formSessionInviteUpdateRecordDecoder: Decoder[Record] = WithId.decoder
  implicit val formSessionInviteFullEncoder: Encoder[Full] = WithId.encoder

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(formSessionInvite: CreateRecord): Result[Record]
    def update(formSessionInvite: UpdateRecord): Result[Full]
    def get(id: Id): Result[Full]
    def getByFormSession(id: FormSession.Id): Result[List[Full]]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Full]]
    def listByFormSession(formSessionId: FormSession.Id, pageSize: Int, offset: Int): Result[List[Full]]
  }

  trait Validation[F[_]] {
    def canCreateSessionInvite(formId: FormSession.Id, companyId: Company.Id): EitherT[F, ValidationError, FormSession.Full]
    def hasOwnership(id: FormSessionInvite.Id, companyId: Company.Id): EitherT[F, ValidationError, Full]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F], formSessionValidation: FormSession.Validation[F]) extends Validation[F] {
    val success = EitherT.rightT[F, ValidationError](())
    val contractFull = EitherT.leftT[F, Unit](ValidationError.CompanyContractFull: ValidationError)
    override def canCreateSessionInvite(formId: FormSession.Id, companyId: Company.Id) =
      formSessionValidation.hasOwnership(formId, companyId)

    override def hasOwnership(id: Id, companyId: Company.Id) = for {
      formSessionInvite <- repo.get(id).leftMap(ValidationError.Repo)
      _ <- formSessionValidation.hasOwnership(formSessionInvite.data.formSession, companyId)
    } yield formSessionInvite
  }

  class Service[F[_] : Monad](repo: Repo[F], validation: Validation[F], userService: User.Service[F]) {
    type Result[T] = EitherT[F, ValidationError, T]

    def create(formSessionInvite: Create, formSessionId: FormSession.Id, companyId: Company.Id): Result[Full] = for {
      _ <- validation.canCreateSessionInvite(formSessionId, companyId)
      user <- userService.getByName(formSessionInvite.user.username)
        .orElse(userService.create(formSessionInvite.user))
      createdFormSessionInvite <- repo.create(formSessionInvite.copy(formSession = formSessionId, user = user.id))
        .leftMap[ValidationError](ValidationError.Repo)
    } yield createdFormSessionInvite.mapData(_.copy(user = user))

    def getById(formSessionInviteId: Id, companyId: Company.Id): Result[Full] =
      validation.hasOwnership(formSessionInviteId, companyId)

    def delete(formSessionInviteId: Id, companyId: Company.Id): Result[Unit] = for {
      _ <- validation.hasOwnership(formSessionInviteId, companyId)
      _ <- repo.delete(formSessionInviteId).leftMap[ValidationError](ValidationError.Repo)
    } yield ()

    def update(formSessionInvite: Update, companyId: Company.Id): Result[Full] = for {
      _ <- validation.hasOwnership(formSessionInvite.id, companyId)
      user <- userService.getByName(formSessionInvite.data.user.username)
        .orElse(userService.create(formSessionInvite.data.user))
      result <- repo.update(formSessionInvite.mapData(_.copy(user = user.id)))
        .leftMap[ValidationError](ValidationError.Repo)
    } yield result

    def listByFormSession(formSessionId: FormSession.Id, pageSize: Int, offset: Int): Result[List[Full]] =
      repo.listByFormSession(formSessionId, pageSize, offset).leftMap(ValidationError.Repo)
  }
}

