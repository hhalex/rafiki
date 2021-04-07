package com.lion.rafiki.domain

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import shapeless.tag
import shapeless.tag.@@

object Form {

  sealed trait Tree {
    val id: Option[Form.Id]
  }

  type Id = Long @@ Tree
  val tagSerial = tag[Tree](_: Long)

  implicit val formIdDecoder: Decoder[Id] = Decoder[Long].map(tagSerial)
  implicit val formIdEncoder: Encoder[Id] = Encoder[Long].contramap(_.asInstanceOf[Long])

  case class TextMD(id: Option[Form.Id], text: String) extends Tree
  case class Question(id: Option[Form.Id], label: String, text: String) extends Tree
  case class Group(id: Option[Form.Id], children: Seq[Tree]) extends Tree

  def createText(text: String): Tree = TextMD(None, text)
  def createQuestion(label: String, text: String): Tree = Question(None, label, text)
  def createGroup(children: Tree*): Tree = Group(None, children)

  def updateText(id: Id, text: String): Tree = TextMD(id.some, text)
  def updateQuestion(id: Id, label: String, text: String): Tree = Question(id.some, label, text)
  def updateGroup(id: Id, children: Tree*): Tree = Group(id.some, children)

  type Create = Tree
  type Update = Tree
  type Record = Tree

  implicit val formDecoder: Decoder[Tree] = List[Decoder[Tree]](
    Decoder[TextMD].widen,
    Decoder[Question].widen,
    Decoder[Group].widen
  ).reduceLeft(_ or _)

  implicit val formEncoder: Encoder[Tree] = Encoder.instance {
    case r @ TextMD(_, _) => r.asJson
    case r @ Question(_, _, _) => r.asJson
    case r @ Group(_, _) => r.asJson
  }
  //implicit val formUpdateDecoder: Decoder[Update] = WithId.decoder
  //implicit val userFullEncoder: Encoder[Record] = WithId.encoder

  trait Repo[F[_]] {
    def create(form: Create): F[Record]
    def update(form: Update): OptionT[F, Record]
    def get(id: Id): OptionT[F, Record]
    def delete(id: Id): OptionT[F, Record]
    def list(pageSize: Int, offset: Int): F[List[Record]]
  }

  trait Validation[F[_]] {
    def doesNotExist(c: Create): EitherT[F, ValidationError, Unit]
    def exists(c: Id): EitherT[F, ValidationError, Unit]
  }

  class Service[F[_] : Monad](repo: Repo[F], validation: Validation[F]) {
    def create(form: Create): EitherT[F, ValidationError, Record] = ???
    def getById(userId: Id): EitherT[F, ValidationError, Record] = ???
    def getByName(userName: String): EitherT[F, ValidationError, Record] = ???
    def delete(userId: Id): F[Unit] = ???
    def update(user: Update): EitherT[F, ValidationError, Record] = ???
    def list(pageSize: Int, offset: Int): F[List[Record]] = ???
  }

}
