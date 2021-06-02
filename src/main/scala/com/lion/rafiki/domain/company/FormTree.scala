package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import com.lion.rafiki.domain.{
  Company,
  RepoError,
  TaggedId,
  ValidationError,
  WithId,
  OrWithIdF,
  WithIdF,
  Fix
}
import WithIdF.given
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import cats.Functor
import cats.Traverse
import cats.Applicative
import cats.Eval
import doobie.util.Get

enum FormTreeP[+T]:
  case Text(text: String) extends FormTreeP[Nothing]
  case Question(
      label: String,
      text: String,
      answers: List[QuestionAnswer]
  ) extends FormTreeP[Nothing]
  case Group(children: List[T])
  def kind: FormTree.Kind = this match
    case _: Text        => FormTree.Kind.Text
    case _: Question    => FormTree.Kind.Question
    case _: Group[_]       => FormTree.Kind.Group
end FormTreeP
object FormTreeP:
  extension [T <: FormTreeP[_]](ftp: T)
    def withId(id: FormTree.Id): WithId[FormTree.Id, T] = WithId(id, ftp)

  given Functor[FormTreeP] with
    def map[A, B](fa: FormTreeP[A])(f: A => B): FormTreeP[B] = fa match
      case t: FormTreeP.Text => t
      case q: FormTreeP.Question => q
      case FormTreeP.Group(children) => FormTreeP.Group(children.map(f))

  given Traverse[FormTreeP] with
    def foldLeft[A, B](fa: FormTreeP[A], b: B)(f: (B, A) => B): B = ???
    def foldRight[A, B](fa: FormTreeP[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = ???
    def traverse[F[_]: Applicative, A, B](fa: FormTreeP[A])(f: A => F[B]): F[FormTreeP[B]] = fa match
      case t: FormTreeP.Text => Applicative[F].pure(t)
      case q: FormTreeP.Question => Applicative[F].pure(q)
      case FormTreeP.Group(children) => children.traverse(f).map(FormTreeP.Group.apply)


type FormTreeF[T] = OrWithIdF[FormTree.Id, FormTreeP][T]
type FormTree = Fix[FormTreeF]
type FormTreeWithIdF[T] = WithIdF[FormTree.Id, FormTreeP][T]
type FormTreeWithId = Fix[FormTreeWithIdF]

trait FormTreeId
object FormTree extends TaggedId[FormTreeId] {

  extension (treeWithId: WithId[FormTree.Id, FormTreeP[?]])
    def key: FormTree.Key = (treeWithId.id, treeWithId.data.kind)
    def kind = treeWithId.data.kind

  val extractLabels: FormTreeP[List[String]] => List[String] = {
    case FormTreeP.Question(label, _, _) => List(label)
    case FormTreeP.Text(_) => Nil
    case FormTreeP.Group(children) => children.flatten
  }

  //def labels(t: FormTree): Set[String] = cataOrWithId[FormTreeP, List[String], FormTree.Id](extractLabels)(t).toSet
  def labels(u: Update): Set[String] = WithIdF.cata[FormTreeP, List[String], FormTree.Id](extractLabels)(u).toSet

  enum Kind(val str: String):
    case Question extends Kind("question")
    case Group extends Kind("group")
    case Text extends Kind("text")

  object Kind:
    def fromStringE(s: String) = s match
      case Kind.Question.str => Kind.Question.asRight
      case Kind.Group.str    => Kind.Group.asRight
      case Kind.Text.str     => Kind.Text.asRight
      case other => Left(s"'$other' is not a member value of FormTree.Kind")
    given Decoder[Kind] = Decoder[String].emap(Kind.fromStringE)
    given Encoder[Kind] = Encoder.instance { _.str.asJson }

  type Key = (Id, Kind)

  import QuestionAnswer.given
  import Id.given

  given [T: Decoder]: Decoder[FormTreeP[T]] =
    deriveDecoder[FormTreeP.Question].widen[FormTreeP[T]]
      .or(deriveDecoder[FormTreeP.Text].widen[FormTreeP[T]])
      .or(deriveDecoder[FormTreeP.Group[T]].widen[FormTreeP[T]])

  given [T: Encoder]: Encoder[FormTreeP[T]] = Encoder.instance {
    case r: FormTreeP.Text     => r.asJson(deriveEncoder[FormTreeP.Text])
    case r: FormTreeP.Question => r.asJson(deriveEncoder[FormTreeP.Question])
    case r: FormTreeP.Group[T]    => r.asJson(deriveEncoder[FormTreeP.Group[T]])
  }
  given [T: Decoder]: Decoder[OrWithIdF[FormTree.Id, FormTreeP][T]] =
    Decoder[FormTreeWithIdF[T]].widen[OrWithIdF[FormTree.Id, FormTreeP][T]]
      .or(Decoder[FormTreeP[T]].widen[OrWithIdF[FormTree.Id, FormTreeP][T]])

  given [T: Encoder]: Encoder[OrWithIdF[FormTree.Id, FormTreeP][T]] = Encoder.instance {
    case r: FormTreeWithIdF[T] => r.asJson
    case r: FormTreeP[T] => r.asJson
  }

  given [T: Decoder]: Decoder[FormTreeWithIdF[T]] = WithId.deriveDecoder
  given [T: Encoder]: Encoder[FormTreeWithIdF[T]] = WithId.deriveEncoder

  import OrWithIdF.given
  given Decoder[FormTree] = Fix.deriveDecoder[FormTreeF]
  given Encoder[FormTree] = Fix.deriveEncoder[FormTreeF]
  given Encoder[Update] = Fix.deriveEncoder[FormTreeWithIdF]

  type Create = Fix[FormTreeP]
  type Update = FormTreeWithId
  type Record = Update
}
