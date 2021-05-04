package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import com.lion.rafiki.domain.{
  Company,
  RepoError,
  TaggedId,
  ValidationError,
  WithId
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

case class Form[T](
    company: Option[Company.Id],
    name: String,
    description: Option[String],
    tree: Option[T]
) {
  def withId(id: Form.Id) = WithId(id, this)
}
trait FormId
object Form extends TaggedId[FormId] {
  extension [T <: TreeP](tree: T) {
    def withId(id: Tree.Id) = WithId(id, tree)
    def kind: Tree.Kind = tree match {
      case _: Tree.Text        => Tree.Kind.Text
      case _: Tree.Question  => Tree.Kind.Question
      case _: Tree.Group       => Tree.Kind.Group
    }

    def labels: Set[String] = {
      def rec(t: Form.TreeP): List[String] = t match {
        case Form.Tree.Question(label, _, _)    => List(label)
        case _: Form.Tree.Text                  => Nil
        case Form.Tree.Group(children)          => children.flatMap({
          case WithId(id, treeP) => rec(treeP)
          case treeP: TreeP => rec(treeP)
        })
      }
      rec(tree).toSet
    }
  }

  extension [T <: TreeP](treeWithId: WithId[Tree.Id, T]) {
    def key = (treeWithId.id, treeWithId.data.kind)
    def kind: Tree.Kind = treeWithId.data.kind
    def labels: Set[String] = treeWithId.data.labels
  }

  type TreeP = Tree.Text | Tree.Question | Tree.Group
  type Tree = TreeP | WithId[Tree.Id, TreeP]
  trait TreeId
  object Tree extends TaggedId[TreeId] {
    sealed trait Kind
    object Kind {
      case object Question extends Kind
      case object Group extends Kind
      case object Text extends Kind
      case class Unknown(s: String) extends Kind

      implicit val formTreeKindDecoder: Decoder[Kind] =
        Decoder[String].emap(Kind.fromStringE)
      implicit val formTreeKindEncoder: Encoder[Kind] =
        Encoder[String].contramap(_.toString)

      def fromString(s: String): Kind = s.toLowerCase match {
        case "question" => Kind.Question
        case "group"    => Kind.Group
        case "text"     => Kind.Text
        case other      => Kind.Unknown(other)
      }

      def fromStringE(s: String) = fromString(s) match {
        case Unknown(other) =>
          Left(s"'$other' is not a member value of Form.Tree.Kind")
        case treeKind => Right(treeKind)
      }
    }

    type Key = (Id, Kind)

    case class Text(text: String)
    case class Question(
        label: String,
        text: String,
        answers: List[Question.Answer]
    )
    case class Group(children: List[Tree])

    object Question {

      sealed trait Answer {
        def withId(id: Answer.Id): AnswerWithId
      }
      sealed trait AnswerWithId extends Answer {
        val id: Answer.Id
      }
      trait AnswerId
      object Answer extends TaggedId[AnswerId] {
        import io.circe.generic.auto._
        case class FreeText(label: Option[String]) extends Answer {
          override def withId(id: Id) = FreeTextWithId(id, label)
        }
        case class FreeTextWithId(id: Id, label: Option[String])
            extends Answer
            with AnswerWithId {
          override def withId(id: Id) = copy(id = id)
        }
        case class Numeric(label: Option[String], value: Int) extends Answer {
          override def withId(id: Id) = NumericWithId(id, label, value)
        }
        case class NumericWithId(id: Id, label: Option[String], value: Int)
            extends Answer
            with AnswerWithId {
          override def withId(id: Id) = copy(id = id)
        }

        implicit val questionAnswerWithIdEncoder: Encoder[AnswerWithId] =
          Encoder.instance {
            case r: FreeTextWithId => r.asJson
            case r: NumericWithId  => r.asJson
          }

        implicit val questionAnswerWithIdDecoder: Decoder[AnswerWithId] =
          List[Decoder[AnswerWithId]](
            Decoder[NumericWithId].widen,
            Decoder[FreeTextWithId].widen
          ).reduceLeft(_ or _)

        implicit val questionAnswerDecoder: Decoder[Answer] =
          List[Decoder[Answer]](
            Decoder[AnswerWithId].widen,
            Decoder[Numeric].widen,
            Decoder[FreeText].widen
          ).reduceLeft(_ or _)

        implicit val questionAnswerEncoder: Encoder[Answer] = Encoder.instance {
          case r: AnswerWithId => r.asJson
          case r: Numeric      => r.asJson
          case r: FreeText     => r.asJson
        }
      }
    }
    // Decoding
    implicit val treeGroupDecoder: Decoder[Group] = deriveDecoder
    implicit val treeTextDecoder: Decoder[Text] = deriveDecoder
    implicit val treeQuestionDecoder: Decoder[Question] = deriveDecoder

    implicit val formTreePDecoder: Decoder[TreeP] =
      Decoder[Question].widen[TreeP]
        .or(Decoder[Text].widen)
        .or(Decoder[Group].widen)

    implicit val formTreeIdDecoder: Decoder[WithId[Tree.Id, TreeP]] = WithId.decoder

    implicit val formTreeDecoder: Decoder[Tree] =
      Decoder[WithId[Tree.Id, TreeP]].widen
        .or(Decoder[TreeP].widen)

    // Encoding
    implicit val treeGroupEncoder: Encoder[Group] = deriveEncoder
    implicit val treeTextEncoder: Encoder[Text] = deriveEncoder
    implicit val treeQuestionEncoder: Encoder[Question] = deriveEncoder

    implicit val formTreePEncoder: Encoder[TreeP] = Encoder.instance {
      case r: Text        => r.asJson
      case r: Question    => r.asJson
      case r: Group       => r.asJson
    }

    implicit val formTreeIdEncoder: Encoder[WithId[Tree.Id, TreeP]] = WithId.encoder

    implicit val formTreeEncoder: Encoder[Tree] = Encoder.instance {
      case r: WithId[Tree.Id, TreeP]        => r.asJson
      case r: TreeP    => r.asJson
    }

    type Create = TreeP
    type Update = WithId[Id, TreeP]
    type Record = Update
  }

  import Tree.{taggedIdDecoder, taggedIdEncoder}
  import Company.{taggedIdDecoder, taggedIdEncoder}
  implicit val formKeyEncoder: Encoder[Tree.Key] = deriveEncoder
  implicit def formDecoder[T: Decoder]: Decoder[Form[T]] = deriveDecoder
  implicit def formEncoder[T: Encoder]: Encoder[Form[T]] = deriveEncoder
  implicit val formCreateDecoder: Decoder[Create] = deriveDecoder
  implicit val formUpdateDecoder: Decoder[Update] = WithId.decoder
  implicit val formRecordEncoder: Encoder[Record] = WithId.encoder
  implicit val formFullEncoder: Encoder[Full] = WithId.encoder

  type Create = Form[Tree]
  type Update = WithId[Id, Create]
  type Record = WithId[Id, Form[Tree.Key]]
  type Full = WithId[Id, Form[Tree.Record]]

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(form: Create): Result[Full]
    def update(form: Update): Result[Full]
    def get(id: Id): Result[Full]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompany(
        company: Company.Id,
        pageSize: Int,
        offset: Int
    ): Result[List[Record]]
  }

  trait Validation[F[_]] {
    def hasOwnership(
        formId: Form.Id,
        companyId: Option[Company.Id]
    ): EitherT[F, ValidationError, Full]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F]) extends Validation[F] {
    val notAllowed = EitherT
      .leftT[F, Form.Full](ValidationError.NotAllowed)
      .leftWiden[ValidationError]
    override def hasOwnership(
        formId: Form.Id,
        companyId: Option[Company.Id]
    ): EitherT[F, ValidationError, Full] = for {
      repoForm <- repo.get(formId).leftMap(ValidationError.Repo)
      success = EitherT.rightT[F, ValidationError](repoForm)
      _ <- (companyId, repoForm.data.company) match {
        case (Some(id), Some(formOwnerId)) if id == formOwnerId => success
        case (None, _)                                          => success
        case _                                                  => notAllowed
      }
    } yield repoForm
  }

  class Service[F[_]: Monad](
      repo: Repo[F],
      inviteAnswerRepo: InviteAnswer.Repo[F],
      validation: Validation[F]
  ) {
    type Result[T] = EitherT[F, ValidationError, T]
    def create(form: Create, companyId: Option[Company.Id]): Result[Full] =
      (for {
        createdForm <- repo.create(form.copy(company = companyId))
        answerTableName = s"form${createdForm.id}_answers"
          .asInstanceOf[InviteAnswer.TableName]
        _ <- createdForm.data.tree.traverse(t =>
          inviteAnswerRepo.overrideAnswerTable(answerTableName, t.labels)
        )
      } yield createdForm).leftMap(ValidationError.Repo)

    def getById(formId: Id, companyId: Option[Company.Id]): Result[Full] = for {
      _ <- validation.hasOwnership(formId, companyId)
      repoForm <- repo
        .get(formId)
        .leftMap[ValidationError](ValidationError.Repo)
    } yield repoForm

    def delete(formId: Id, companyId: Option[Company.Id]): Result[Unit] = for {
      _ <- validation.hasOwnership(formId, companyId)
      _ <- repo.delete(formId).leftMap[ValidationError](ValidationError.Repo)
    } yield ()

    def update(form: Update, companyId: Option[Company.Id]): Result[Full] =
      for {
        formDB <- validation.hasOwnership(form.id, companyId)
        result <- repo
          .update(form.mapData(_.copy(company = formDB.data.company)))
          .leftMap[ValidationError](ValidationError.Repo)
        answerTableName = s"form${result.id}_answers"
          .asInstanceOf[InviteAnswer.TableName]
        _ <- result.data.tree
          .traverse(t =>
            inviteAnswerRepo.overrideAnswerTable(answerTableName, t.labels)
          )
          .leftMap(ValidationError.Repo)
      } yield result

    def listByCompany(
        companyId: Company.Id,
        pageSize: Int,
        offset: Int
    ): Result[List[Record]] =
      repo
        .listByCompany(companyId, pageSize, offset)
        .leftMap(ValidationError.Repo)
  }
}
