package com.lion.rafiki.domain.company

import com.lion.rafiki.domain.WithId
import com.lion.rafiki.domain.{RepoError, WithId}
import cats.data.EitherT
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import com.lion.rafiki.domain.{TaggedId, ValidationError, User}
import cats.Monad
import cats.syntax.all._

case class InviteAnswer(values: Map[QuestionAnswer.Id, Option[String]]):
  def withId(id: SessionInvite.Id) = WithId(id, this)

trait InviteAnswerId
object InviteAnswer {
  type Create = InviteAnswer
  type Update = WithId[SessionInvite.Id, Create]
  type Record = Update
  type Full = Update

  import SessionInvite.Id.given
  import QuestionAnswer.Id.given
  import SessionInvite.Id

  given KeyDecoder[QuestionAnswer.Id] = KeyDecoder.decodeKeyLong.map(QuestionAnswer.tag)
  given KeyEncoder[QuestionAnswer.Id] = KeyEncoder.encodeKeyLong.contramap(QuestionAnswer.unTag)
  given Decoder[Map[QuestionAnswer.Id, Option[String]]] = Decoder.decodeMap
  given Encoder[Map[QuestionAnswer.Id, Option[String]]] = Encoder.encodeMap

  given Decoder[Create] = deriveDecoder
  given Encoder[Create] = deriveEncoder
  given Encoder[Update] = WithId.deriveEncoder

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(inviteAnswer: Create, sessionInviteId: Id): Result[Record]
    def update(inviteAnswer: Update): Result[Record]
    def get(id: Id): Result[Record]
    def delete(id: Id): Result[Unit]
  }
  class Service[F[_]: Monad](repo: Repo[F], sessionInviteValidation: SessionInvite.Validation[F]) {
    type Result[T] = EitherT[F, ValidationError | RepoError, T]
    def create(inviteAnswer: Create, sessionInviteId: Id, userId: User.Id): Result[Record] = for
      _ <- sessionInviteValidation.hasUserOwnership(sessionInviteId, userId).leftWiden
      _ <- sessionInviteValidation.isValidAnswer(inviteAnswer, sessionInviteId).leftWiden
      createdInviteAnswer <- repo.create(inviteAnswer, sessionInviteId).leftWiden
    yield createdInviteAnswer

    def update(inviteAnswer: Update, userId: User.Id): Result[Record] = for
      _ <- sessionInviteValidation.hasUserOwnership(inviteAnswer.id, userId).leftWiden
      _ <- sessionInviteValidation.isValidAnswer(inviteAnswer.data, inviteAnswer.id).leftWiden
      updatedInviteAnswer <- repo.update(inviteAnswer).leftWiden
    yield updatedInviteAnswer
   }
}
