package com.lion.rafiki.domain

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.implicits._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.circe.syntax.EncoderOps
import shapeless.tag
import shapeless.tag.@@

case class Form[T](company: Option[Company.Id], name: String, description: Option[String], tree: Option[T]) {
  def withId(id: Form.Id) = WithId(id, this)
}

object Form {

  type Id = Long @@ Form[_]
  val tagSerial = tag[Form[_]](_: Long)

  sealed trait Tree {
    lazy val kind = this match {
      case _: Tree.Text | _: Tree.TextWithKey => Tree.Kind.Text
      case _: Tree.Question | _: Tree.QuestionWithKey => Tree.Kind.Question
      case _: Tree.Group | _: Tree.GroupWithKey => Tree.Kind.Group
    }
    def withKey(id: Tree.Id): TreeWithKey
  }
  sealed trait TreeWithKey extends Tree {
    val id: Tree.Id
    lazy val key = (id, kind)
  }

  object Tree {
    import io.circe.generic.auto._

    sealed trait Kind
    object Kind {
      case object Question extends Kind
      case object Group extends Kind
      case object Text extends Kind
      case class Unknown(s: String) extends Kind

      implicit val formTreeKindDecoder: Decoder[Kind] = Decoder[String].emap(Kind.fromStringE)
      implicit val formTreeKindEncoder: Encoder[Kind] = Encoder[String].contramap(_.toString)

      def fromString(s: String): Kind = s.toLowerCase match {
        case "question" => Kind.Question
        case "group" => Kind.Group
        case "text" => Kind.Text
        case other => Kind.Unknown(other)
      }

      def fromStringE(s: String) = fromString(s) match {
        case Unknown(other) => Left(s"'$other' is not a member value of Form.Tree.Kind")
        case treeKind => Right(treeKind)
      }
    }

    type Key = (Id, Kind)
    type Id = Long @@ Tree
    val tagSerial = tag[Tree](_: Long)

    implicit val formTreeIdDecoder: Decoder[Id] = Decoder[Long].map(tagSerial)
    implicit val formTreeIdEncoder: Encoder[Id] = Encoder[Long].contramap(_.asInstanceOf[Long])

    case class Text(text: String) extends Tree {
      override def withKey(id: Id) = TextWithKey(id, text)
    }
    case class TextWithKey(id: Id, text: String) extends Tree with TreeWithKey {
      override def withKey(id: Id) = copy(id = id)
    }
    case class Question(label: String, text: String) extends Tree{
      override def withKey(id: Id) = QuestionWithKey(id, label, text)
    }
    case class QuestionWithKey(id: Id, label: String, text: String) extends Tree with TreeWithKey {
      override def withKey(id: Id) = copy(id = id)
    }
    case class Group(children: List[Tree]) extends Tree{
      override def withKey(id: Id) = GroupWithKey(id, children)
    }
    case class GroupWithKey(id: Id, children: List[Tree]) extends Tree with TreeWithKey {
      override def withKey(id: Id) = copy(id = id)
    }

    type Create = Tree
    type Update = TreeWithKey
    type Record = TreeWithKey

    implicit val formTreeDecoder: Decoder[Tree] = List[Decoder[Tree]](
      Decoder[Text].widen,
      Decoder[TextWithKey].widen,
      Decoder[Question].widen,
      Decoder[QuestionWithKey].widen,
      Decoder[Group].widen,
      Decoder[GroupWithKey].widen
    ).reduceLeft(_ or _)

    implicit val formTreeEncoder: Encoder[Tree] = Encoder.instance {
      case r: Text => r.asJson
      case r: TextWithKey => r.asJson
      case r: Question => r.asJson
      case r: QuestionWithKey => r.asJson
      case r: Group => r.asJson
      case r: GroupWithKey => r.asJson
    }
  }

  implicit val formIdDecoder: Decoder[Id] = Decoder[Long].map(tagSerial)
  implicit val formIdEncoder: Encoder[Id] = Encoder[Long].contramap(_.asInstanceOf[Long])
  implicit def formDecoder[T: Decoder]: Decoder[Form[T]] = deriveDecoder
  implicit def formEncoder[T: Encoder]: Encoder[Form[T]] = deriveEncoder
  implicit val formCreateDecoder: Decoder[Create] = deriveDecoder
  implicit val formUpdateDecoder: Decoder[Update] = WithId.decoder
  implicit val formRecordEncoder: Encoder[Record] = WithId.encoder
  implicit val formFullEncoder: Encoder[Full] = WithId.encoder

  type Create = Form[Tree.Key]
  type Update = WithId[Id, Form[Tree.Key]]
  type Record = Update
  type Full = WithId[Id, Form[Tree]]

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(form: Create): Result[Record]
    def update(form: Update): Result[Record]
    def syncTree(tree: Tree, parent: Option[Tree.Key]): Result[Tree]
    def get(id: Id): Result[Record]
    def getWithTree(id: Id): Result[Full]
    def delete(id: Id): Result[Record]
    def deleteTree(key: Tree.Key): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompany(company: Company.Id, pageSize: Int, offset: Int): Result[List[Record]]
  }

  trait Validation[F[_]] {
    def doesNotExist(c: Create): EitherT[F, ValidationError, Unit]
    def exists(c: Id): EitherT[F, ValidationError, Unit]
  }

  class Service[F[_] : Monad](repo: Repo[F]) {
    type Result[T] = EitherT[F, ValidationError, T]
    // forms
    def create(form: Create): Result[Record] = {
      repo.create(form).leftMap(ValidationError.Repo)
    }
    def getById(formId: Id): Result[Record] = {
      repo.get(formId).leftMap(ValidationError.Repo)
    }
    def delete(formId: Id): Result[Unit] = {
      repo.delete(formId).as(()).leftMap(ValidationError.Repo)
    }
    def update(form: Update): Result[Record] = {
      repo.update(form).leftMap(ValidationError.Repo)
    }
    def listByCompany(companyId: Company.Id, pageSize: Int, offset: Int): Result[List[Record]] =
      repo.listByCompany(companyId, pageSize, offset).leftMap(ValidationError.Repo)
  }
}
