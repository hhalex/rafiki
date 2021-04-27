package com.lion.rafiki.domain.company

import cats.data.EitherT
import com.lion.rafiki.domain.{Company, RepoError, TaggedId, User, WithId}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}


case class FormSessionInvite[User](formSession: FormSession.Id, user: User, acceptConditions: Boolean) {
  def withId(id: FormSessionInvite.Id) = WithId(id, this)
}

object FormSessionInvite extends TaggedId[FormSessionInvite[_]] {

  type Create = FormSessionInvite[User.Create]
  type Update = WithId[Id, FormSessionInvite[User.Update]]
  type Record = Update
  type Full = WithId[Id, FormSessionInvite[User.Full]]

  implicit def formSessionInviteDecoder[T: Decoder]: Decoder[FormSessionInvite[T]] = deriveDecoder
  implicit def formSessionInviteEncoder[T: Encoder]: Encoder[FormSessionInvite[T]] = deriveEncoder
  implicit val formSessionInviteCreateDecoder: Decoder[Create] = deriveDecoder
  implicit val formSessionInviteCreateEncoder: Encoder[Create] = deriveEncoder
  implicit val formSessionInviteUpdateDecoder: Decoder[Update] = WithId.decoder
  implicit val formSessionInviteFullEncoder: Encoder[Full] = WithId.encoder

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(formSessionInvite: Create): Result[Full]
    def update(formSessionInvite: Update): Result[Full]
    def get(id: Id): Result[Full]
    def getBySession(id: Id): Result[List[Full]]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompany(companyId: Company.Id, pageSize: Int, offset: Int): Result[List[Record]]
  }
}


