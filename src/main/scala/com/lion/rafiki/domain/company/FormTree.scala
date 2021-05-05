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

type FormTreeP = FormTree.Text | FormTree.Question | FormTree.Group
type FormTree = FormTreeP | WithId[FormTree.Id, FormTreeP]

trait FormTreeId
object FormTree extends TaggedId[FormTreeId] {

  extension [T <: FormTreeP](tree: T) {
    def withId(id: FormTree.Id) = WithId(id, tree)
    def kind: FormTree.Kind = tree match {
      case _: FormTree.Text        => FormTree.Kind.Text
      case _: FormTree.Question  => FormTree.Kind.Question
      case _: FormTree.Group       => FormTree.Kind.Group
    }

    def labels: Set[String] = {
      def rec(t: FormTreeP): List[String] = t match {
        case FormTree.Question(label, _, _)    => List(label)
        case _: FormTree.Text                  => Nil
        case FormTree.Group(children)          => children.flatMap({
          case WithId(id, treeP) => rec(treeP)
          case treeP: FormTreeP => rec(treeP)
        })
      }
      rec(tree).toSet
    }
  }

extension [T <: FormTreeP](treeWithId: WithId[FormTree.Id, T]) {
    def key = (treeWithId.id, treeWithId.data.kind)
    def kind: FormTree.Kind = treeWithId.data.kind
    def labels: Set[String] = treeWithId.data.labels
  }

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
      answers: List[QuestionAnswer]
  )
  case class Group(children: List[FormTree])

// Decoding
  implicit val treeGroupDecoder: Decoder[Group] = deriveDecoder
  implicit val treeTextDecoder: Decoder[Text] = deriveDecoder
  implicit val treeQuestionDecoder: Decoder[Question] = deriveDecoder

  implicit val formTreePDecoder: Decoder[FormTreeP] =
    Decoder[Question].widen
      .or(Decoder[Text].widen)
      .or(Decoder[Group].widen)

  implicit val formTreeIdDecoder: Decoder[WithId[FormTree.Id, FormTreeP]] =
    WithId.decoder

  implicit val formTreeDecoder: Decoder[FormTree] =
    Decoder[WithId[FormTree.Id, FormTreeP]].widen
      .or(Decoder[FormTreeP].widen)

// Encoding
  implicit val treeGroupEncoder: Encoder[Group] = deriveEncoder
  implicit val treeTextEncoder: Encoder[Text] = deriveEncoder
  implicit val treeQuestionEncoder: Encoder[Question] = deriveEncoder

  implicit val formTreePEncoder: Encoder[FormTreeP] = Encoder.instance {
    case r: Text     => r.asJson
    case r: Question => r.asJson
    case r: Group    => r.asJson
  }

  implicit val formTreeIdEncoder: Encoder[WithId[FormTree.Id, FormTreeP]] =
    WithId.encoder

  implicit val formTreeEncoder: Encoder[FormTree] = Encoder.instance {
    case r: WithId[FormTree.Id, FormTreeP] => r.asJson
    case r: FormTreeP                  => r.asJson
  }

  type Create = FormTreeP
  type Update = WithId[Id, FormTreeP]
  type Record = Update
}
