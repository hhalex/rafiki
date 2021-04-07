package com.lion.rafiki.domain

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.syntax.EncoderOps
import shapeless.tag
import shapeless.tag.@@

case class Form[T](owner: Option[Company.Id], name: String, description: Option[String], tree: T) {
  def withId(id: Form.Id) = WithId(id, this)
}

object Form {

  type Id = Long @@ Form[_]
  val tagSerial = tag[Form[_]](_: Long)

  sealed trait Tree {
    val id: Option[Tree.Id]
  }

  object Tree {
    import io.circe.generic.auto._

    type Id = Long @@ Tree
    val tagSerial = tag[Tree](_: Long)

    implicit val formTreeIdDecoder: Decoder[Id] = Decoder[Long].map(tagSerial)
    implicit val formTreeIdEncoder: Encoder[Id] = Encoder[Long].contramap(_.asInstanceOf[Long])

    case class TextMD(id: Option[Id], text: String) extends Tree
    case class Question(id: Option[Id], label: String, text: String) extends Tree
    case class Group(id: Option[Id], children: Seq[Tree]) extends Tree

    def createText(text: String): Tree = TextMD(None, text)
    def createQuestion(label: String, text: String): Tree = Question(None, label, text)
    def createGroup(children: Tree*): Tree = Group(None, children)

    def updateText(id: Id, text: String): Tree = TextMD(id.some, text)
    def updateQuestion(id: Id, label: String, text: String): Tree = Question(id.some, label, text)
    def updateGroup(id: Id, children: Tree*): Tree = Group(id.some, children)

    type Create = Tree
    type Update = Tree
    type Record = Tree

    implicit val formTreeDecoder: Decoder[Tree] = List[Decoder[Tree]](
      Decoder[TextMD].widen,
      Decoder[Question].widen,
      Decoder[Group].widen
    ).reduceLeft(_ or _)

    implicit val formTreeEncoder: Encoder[Tree] = Encoder.instance {
      case r@TextMD(_, _) => r.asJson
      case r@Question(_, _, _) => r.asJson
      case r@Group(_, _) => r.asJson
    }
  }

  type Create = Form[Tree]
  type Update = WithId[Id, Form[Tree]]
  type Record = Update

  trait Repo[F[_]] {
    def create(form: Create): F[Record]
    def update(form: Update): OptionT[F, Record]
    def get(id: Id): OptionT[F, Record]
    def delete(id: Id): OptionT[F, Record]
    def list(pageSize: Int, offset: Int): F[List[Record]]
    def listByCompany(company: Company.Id, pageSize: Int, offset: Int): F[List[Record]]
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
