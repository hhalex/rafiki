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
  enum Kind:
    case Question, Group, Text

  object Kind:
    def fromStringE(s: String) = s match
      case "question" => Kind.Question.asRight
      case "group"    => Kind.Group.asRight
      case "text"     => Kind.Text.asRight
      case other => Left(s"'$other' is not a member value of FormTree.Kind")
    implicit val formTreeKindDecoder: Decoder[Kind] = Decoder[String].emap(Kind.fromStringE)
    implicit val formTreeKindEncoder: Encoder[Kind] = Encoder[String].contramap(_.toString)

  extension [T <: FormTreeP](tree: T)
    def withId(id: Id) = WithId(id, tree)
    def kind: Kind = tree match
      case _: Text        => Kind.Text
      case _: Question  => Kind.Question
      case _: Group       => Kind.Group

    def labels: Set[String] =
      def rec(t: FormTreeP): List[String] = t match
        case Question(label, _, _)    => List(label)
        case _: Text                  => Nil
        case Group(children)          => children.flatMap({
          case WithId(id, treeP) => rec(treeP)
          case treeP: FormTreeP => rec(treeP)
        })
      rec(tree).toSet
    end labels

  extension [T <: FormTreeP](treeWithId: WithId[FormTree.Id, T])
    def key = (treeWithId.id, treeWithId.data.kind)
    def kind: Kind = treeWithId.data.kind
    def labels: Set[String] = treeWithId.data.labels

  type Key = (Id, Kind)

  case class Text(text: String)
  case class Question(
      label: String,
      text: String,
      answers: List[QuestionAnswer]
  )
  case class Group(children: List[FormTree])

// Decoding
  implicit val formTreePDecoder: Decoder[FormTreeP] =
    deriveDecoder[Question].widen
      .or(deriveDecoder[Text].widen)
      .or(deriveDecoder[Group].widen)

  implicit val formTreeIdDecoder: Decoder[WithId[FormTree.Id, FormTreeP]] =
    WithId.decoder

  implicit val formTreeDecoder: Decoder[FormTree] =
    Decoder[WithId[FormTree.Id, FormTreeP]].widen
      .or(Decoder[FormTreeP].widen)

// Encoding
  implicit val formTreePEncoder: Encoder[FormTreeP] = Encoder.instance {
    case r: Text     => r.asJson(deriveEncoder[Text])
    case r: Question => r.asJson(deriveEncoder[Question])
    case r: Group    => r.asJson(deriveEncoder[Group])
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
