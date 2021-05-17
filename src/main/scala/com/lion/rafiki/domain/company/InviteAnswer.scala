package com.lion.rafiki.domain.company

import com.lion.rafiki.domain.WithId
import com.lion.rafiki.domain.{RepoError, WithId}
import cats.data.EitherT
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

type Label = String
case class AnswerValue(numeric: Option[Int], freetext: Option[String])

object AnswerValue:
  given Decoder[AnswerValue] = deriveDecoder
  given Encoder[AnswerValue] = deriveEncoder

case class InviteAnswer(values: Map[Label, AnswerValue]):
  def withId(id: SessionInvite.Id) = WithId(id, this)

object InviteAnswer {
  opaque type TableName <: String = String

  type Create = InviteAnswer
  type Update = WithId[SessionInvite.Id, Create]
  type Record = Update
  type Full = Update

  given Decoder[Create] = deriveDecoder
  given Encoder[Create] = deriveEncoder

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def overrideAnswerTable(id: TableName, labels: Set[String]): Result[Unit]
  }
}
