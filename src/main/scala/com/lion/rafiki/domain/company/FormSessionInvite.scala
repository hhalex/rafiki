package com.lion.rafiki.domain.company

import cats.data.EitherT
import com.lion.rafiki.domain.{RepoError, TaggedId, User, WithId}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}


case class FormSessionInvite[User](formSession: FormSession.Id, user: User, acceptConditions: Option[Boolean]) {
  def withId(id: FormSessionInvite.Id) = WithId(id, this)
}

object FormSessionInvite extends TaggedId[FormSessionInvite[_]] {

  type Create = FormSessionInvite[User.Create]
  type CreateRecord = FormSessionInvite[User.Id]
  type Update = WithId[Id, FormSessionInvite[User.Update]]
  type Record = WithId[Id, FormSessionInvite[User.Id]]
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
    def update(formSessionInvite: UpdateRecord): Result[Record]
    def get(id: Id): Result[Record]
    def getByFormSession(id: FormSession.Id): Result[List[Record]]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByFormSession(formSessionId: FormSession.Id, pageSize: Int, offset: Int): Result[List[Record]]
  }
}


