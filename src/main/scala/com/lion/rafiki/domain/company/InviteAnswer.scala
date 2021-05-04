package com.lion.rafiki.domain.company

import com.lion.rafiki.domain.WithId
import com.lion.rafiki.domain.{RepoError, WithId}
import cats.data.EitherT
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

type Label = String
case class AnswerValue(numeric: Option[Int], freetext: Option[String]) 

case class InviteAnswer(values: Map[Label, AnswerValue]) {
  def withId(id: FormSessionInvite.Id) = WithId(id, this)
}

object InviteAnswer {
  opaque type TableName <: String = String

  type Create = InviteAnswer
  type Update = WithId[FormSessionInvite.Id, Create]
  type Record = Update
  type Full = Update

  implicit val answerValueCreateDecoder: Decoder[AnswerValue] = deriveDecoder
  implicit val answerValueCreateEncoder: Encoder[AnswerValue] = deriveEncoder
  implicit val inviteCreateDecoder: Decoder[Create] = deriveDecoder
  implicit val inviteCreateEncoder: Encoder[Create] = deriveEncoder
  implicit val inviteUpdateDecoder: Decoder[Update] = WithId.decoder
  implicit val inviteFullEncoder: Encoder[Full] = WithId.encoder

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def overrideAnswerTable(id: TableName, labels: Set[String]): Result[Unit]
    
  }
}
