package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import com.lion.rafiki.domain.{Company, RepoError, TaggedId, ValidationError, WithId}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

case class Form[T](company: Option[Company.Id], name: String, description: Option[String], tree: Option[T]) {
  def withId(id: Form.Id) = WithId(id, this)
}

object Form extends TaggedId[Form[_]] {
  sealed trait Tree {
    val kind = this match {
      case _: Tree.Text | _: Tree.TextWithKey => Tree.Kind.Text
      case _: Tree.Question | _: Tree.QuestionWithKey => Tree.Kind.Question
      case _: Tree.Group | _: Tree.GroupWithKey => Tree.Kind.Group
    }
    def withKey(id: Tree.Id): TreeWithKey
  }
  sealed trait TreeWithKey extends Tree {
    val id: Tree.Id
    val key = (id, kind)
  }

  object Tree extends TaggedId[Tree] {
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

    case class Text(text: String) extends Tree {
      override def withKey(id: Id) = TextWithKey(id, text)
    }
    case class TextWithKey(id: Id, text: String) extends Tree with TreeWithKey {
      override def withKey(id: Id) = copy(id = id)
    }
    case class Question(label: String, text: String, answers: List[Question.Answer]) extends Tree {
      override def withKey(id: Id) = QuestionWithKey(id, label, text, answers)
    }
    case class QuestionWithKey(id: Tree.Id, label: String, text: String, answers: List[Question.Answer]) extends Tree with TreeWithKey {
      override def withKey(id: Id) = copy(id = id)
    }
    case class Group(children: List[Tree]) extends Tree{
      override def withKey(id: Id) = GroupWithKey(id, children)
    }
    case class GroupWithKey(id: Id, children: List[Tree]) extends Tree with TreeWithKey {
      override def withKey(id: Id) = copy(id = id)
    }

    object Question {

      sealed trait Answer {
        def withId(id: Answer.Id): AnswerWithId
      }
      sealed trait AnswerWithId extends Answer {
        val id: Answer.Id
      }
      object Answer extends TaggedId[Answer] {

        case class FreeText(label: Option[String]) extends Answer {
          override def withId(id: Id) = FreeTextWithId(id, label)
        }
        case class FreeTextWithId(id: Id, label: Option[String]) extends Answer with AnswerWithId {
          override def withId(id: Id) = copy(id = id)
        }
        case class Numeric(label: Option[String], value: Int) extends Answer {
          override def withId(id: Id) = NumericWithId(id, label, value)
        }
        case class NumericWithId(id: Id, label: Option[String], value: Int) extends Answer with AnswerWithId {
          override def withId(id: Id) = copy(id = id)
        }

        implicit val questionAnswerWithIdEncoder: Encoder[AnswerWithId] = Encoder.instance {
          case r: FreeTextWithId => r.asJson
          case r: NumericWithId => r.asJson
        }

        implicit val questionAnswerWithIdDecoder: Decoder[AnswerWithId] = List[Decoder[AnswerWithId]](
          Decoder[NumericWithId].widen,
          Decoder[FreeTextWithId].widen
        ).reduceLeft(_ or _)

        implicit val questionAnswerDecoder: Decoder[Answer] = List[Decoder[Answer]](
          Decoder[AnswerWithId].widen,
          Decoder[Numeric].widen,
          Decoder[FreeText].widen
        ).reduceLeft(_ or _)

        implicit val questionAnswerEncoder: Encoder[Answer] = Encoder.instance {
          case r: AnswerWithId => r.asJson
          case r: Numeric => r.asJson
          case r: FreeText => r.asJson
        }
      }
    }

    implicit val formTreeWithKeyEncoder: Encoder[TreeWithKey] = Encoder.instance {
      case r: TextWithKey => r.asJson
      case r: QuestionWithKey => r.asJson
      case r: GroupWithKey => r.asJson
    }

    implicit val formTreeWithKeyDecoder: Decoder[TreeWithKey] = List[Decoder[TreeWithKey]](
      Decoder[QuestionWithKey].widen,
      Decoder[TextWithKey].widen,
      Decoder[GroupWithKey].widen
    ).reduceLeft(_ or _)

    implicit val formTreeDecoder: Decoder[Tree] = List[Decoder[Tree]](
      Decoder[TreeWithKey].widen,
      Decoder[Question].widen,
      Decoder[Text].widen,
      Decoder[Group].widen
    ).reduceLeft(_ or _)

    implicit val formTreeEncoder: Encoder[Tree] = Encoder.instance {
      case r: TreeWithKey => r.asJson
      case r: Text => r.asJson
      case r: Question => r.asJson
      case r: Group => r.asJson
    }

    type Create = Tree
    type Update = TreeWithKey
    type Record = TreeWithKey
  }

  implicit def formDecoder[T: Decoder]: Decoder[Form[T]] = deriveDecoder
  implicit def formEncoder[T: Encoder]: Encoder[Form[T]] = deriveEncoder
  implicit val formCreateDecoder: Decoder[Create] = deriveDecoder
  implicit val formUpdateDecoder: Decoder[Update] = WithId.decoder
  implicit val formRecordEncoder: Encoder[Record] = WithId.encoder
  implicit val formFullEncoder: Encoder[Full] = WithId.encoder

  type Create = Form[Tree]
  type Update = WithId[Id, Create]
  type Record = WithId[Id, Form[Tree.Key]]
  type Full = WithId[Id, Form[TreeWithKey]]

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(form: Create): Result[Full]
    def update(form: Update): Result[Full]
    def get(id: Id): Result[Full]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompany(company: Company.Id, pageSize: Int, offset: Int): Result[List[Record]]
  }

  trait Validation[F[_]] {
    def hasOwnership(formId: Form.Id, companyId: Option[Company.Id]): EitherT[F, ValidationError, Full]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F]) extends Validation[F] {
    val notAllowed = EitherT.leftT[F, Form.Full](ValidationError.NotAllowed).leftWiden[ValidationError]
    override def hasOwnership(formId: Form.Id, companyId: Option[Company.Id]): EitherT[F, ValidationError, Full] = for {
      repoForm <- repo.get(formId).leftMap(ValidationError.Repo)
      success = EitherT.rightT[F, ValidationError](repoForm)
      _ <- (companyId, repoForm.data.company) match {
        case (Some(id), Some(formOwnerId)) if id == formOwnerId => success
        case (None, _) => success
        case _ => notAllowed
      }
    } yield repoForm
  }

  class Service[F[_] : Monad](repo: Repo[F], validation: Validation[F]) {
    type Result[T] = EitherT[F, ValidationError, T]
    def create(form: Create, companyId: Option[Company.Id]): Result[Full] = {
      repo.create(form.copy(company = companyId)).leftMap(ValidationError.Repo)
    }

    def getById(formId: Id, companyId: Option[Company.Id]): Result[Full] = for {
      _ <- validation.hasOwnership(formId, companyId)
      repoForm <- repo.get(formId).leftMap[ValidationError](ValidationError.Repo)
    } yield repoForm

    def delete(formId: Id, companyId: Option[Company.Id]): Result[Unit] = for {
      _ <- validation.hasOwnership(formId, companyId)
      _ <- repo.delete(formId).leftMap[ValidationError](ValidationError.Repo)
    } yield ()

    def update(form: Update, companyId: Option[Company.Id]): Result[Full] = for {
      formDB <- validation.hasOwnership(form.id, companyId)
      result <- repo.update(form.mapData(_.copy(company = formDB.data.company))).leftMap[ValidationError](ValidationError.Repo)
    } yield result

    def listByCompany(companyId: Company.Id, pageSize: Int, offset: Int): Result[List[Record]] =
      repo.listByCompany(companyId, pageSize, offset).leftMap(ValidationError.Repo)
  }
}
