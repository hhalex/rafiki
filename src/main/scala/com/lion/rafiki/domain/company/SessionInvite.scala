package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import cats.syntax.all._
import com.lion.rafiki.domain.{Company, RepoError, TaggedId, User, ValidationError, WithId}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import com.lion.rafiki.auth.PasswordError

case class SessionInvite[User](user: User, team: String, acceptConditions: Option[Boolean]) {
  def withId(id: SessionInvite.Id) = WithId(id, this)
}
trait InviteId
object SessionInvite extends TaggedId[InviteId] {

  import User.Id.given
  import Id.given
  type Create = SessionInvite[User.Create]
  type CreateRecord = SessionInvite[User.Id]
  type Update = WithId[Id, Create]
  type Record = WithId[Id, CreateRecord]
  type RecordWithEmail = WithId[Id, SessionInvite[String]]
  type UpdateRecord = Record
  type Full = WithId[Id, SessionInvite[User.Full]]

  given [T: Decoder]: Decoder[SessionInvite[T]] = deriveDecoder
  given [T: Encoder]: Encoder[SessionInvite[T]] = deriveEncoder
  given Decoder[Create] = deriveDecoder
  given Decoder[CreateRecord] = deriveDecoder
  given Encoder[Full] = WithId.deriveEncoder
  given Encoder[RecordWithEmail] = WithId.deriveEncoder
  given Encoder[Record] = WithId.deriveEncoder

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(sessionInvite: CreateRecord, formSessionId: FormSession.Id): Result[Record]
    def update(sessionInvite: UpdateRecord): Result[Record]
    def get(id: Id): Result[(FormSession.Id, RecordWithEmail)]
    def getByUserEmail(email: String): Result[List[(FormSession.Id, RecordWithEmail)]]
    def getByUserId(userId: User.Id): Result[List[(FormSession.Id, RecordWithEmail)]]
    def getByFormSession(id: FormSession.Id): Result[List[RecordWithEmail]]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[(FormSession.Id, SessionInvite.RecordWithEmail)]]
    def listByFormSession(formSessionId: FormSession.Id, pageSize: Int, offset: Int): Result[List[RecordWithEmail]]
  }

  trait Validation[F[_]] {
    def canCreateSessionInvite(formId: FormSession.Id, companyId: Company.Id): EitherT[F, ValidationError | RepoError, FormSession.Full]
    def hasOwnership(id: SessionInvite.Id, companyId: Company.Id): EitherT[F, ValidationError | RepoError, RecordWithEmail]
    def hasUserOwnership(id: SessionInvite.Id, userId: User.Id): EitherT[F, ValidationError | RepoError, RecordWithEmail]
    def isValidAnswer(answer: InviteAnswer, id: SessionInvite.Id): EitherT[F, ValidationError | RepoError, InviteAnswer]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F], formSessionValidation: FormSession.Validation[F]) extends Validation[F] {
    val success = EitherT.rightT[F, ValidationError | RepoError](())
    val contractFull = EitherT.leftT[F, Unit](ValidationError.CompanyContractFull: ValidationError)
    override def canCreateSessionInvite(formId: FormSession.Id, companyId: Company.Id) =
      formSessionValidation.hasOwnership(formId, companyId)

    override def hasOwnership(id: Id, companyId: Company.Id) = for
      sessionInvite <- repo.get(id).leftWiden
      _ <- formSessionValidation.hasOwnership(sessionInvite._1, companyId)
    yield sessionInvite._2

    override def hasUserOwnership(id: Id, userId: User.Id) = for
      sessionInvites <- repo.getByUserId(userId).leftWiden
      invite <- EitherT.fromOption(sessionInvites.find(_._2.id == id), ValidationError.NotAllowed)
    yield invite._2

    override def isValidAnswer(answer: InviteAnswer, id: SessionInvite.Id): EitherT[F, ValidationError | RepoError, InviteAnswer] = ???
  }

  class Service[F[_] : Monad](repo: Repo[F], validation: Validation[F], formSessionValidation: FormSession.Validation[F], userService: User.Service[F]) {
    type Result[T] = EitherT[F, ValidationError | RepoError, T]

    def create(sessionInvite: SessionInvite[String], formSessionId: FormSession.Id, companyId: Company.Id): EitherT[F, ValidationError | RepoError | PasswordError, Full] = for
      _ <- validation.canCreateSessionInvite(formSessionId, companyId).leftWiden
      user <- userService.getByName(sessionInvite.user)
        .orElse(userService.create(User[String](None, None, sessionInvite.user, "pass"))).leftWiden
      createdSessionInvite <- repo.create(sessionInvite.copy(user = user.id), formSessionId)
        .leftWiden
    yield createdSessionInvite.mapData(_.copy(user = user))

    def getById(sessionInviteId: Id, companyId: Company.Id): Result[RecordWithEmail] =
      validation.hasOwnership(sessionInviteId, companyId)

    def delete(sessionInviteId: Id, companyId: Company.Id): Result[Unit] = for
      _ <- validation.hasOwnership(sessionInviteId, companyId)
      _ <- repo.delete(sessionInviteId).leftWiden
    yield ()

    def update(sessionInvite: RecordWithEmail, companyId: Company.Id): EitherT[F, ValidationError | RepoError | PasswordError, Record] = for
      _ <- validation.hasOwnership(sessionInvite.id, companyId).leftWiden
      user <- userService.getByName(sessionInvite.data.user)
        .orElse(userService.create(User[String](None, None, sessionInvite.data.user, "pass")))
      result <- repo.update(sessionInvite.mapData(_.copy(user = user.id)))
        .leftWiden
    yield result

    def listByFormSession(formSessionId: FormSession.Id, companyId: Company.Id, pageSize: Int, offset: Int): Result[List[RecordWithEmail]] = for
      _ <- formSessionValidation.hasOwnership(formSessionId, companyId)
      list <- repo.listByFormSession(formSessionId, pageSize, offset).leftWiden
    yield list

    def getByFormSession(formSessionId: FormSession.Id, companyId: Company.Id): Result[List[RecordWithEmail]] = for
      _ <- formSessionValidation.hasOwnership(formSessionId, companyId)
      list <- repo.getByFormSession(formSessionId).leftWiden
    yield list
  }
}


