package com.lion.rafiki.domain

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import cats.syntax.all._
import cats.Functor
import cats.Traverse
import cats.Applicative
import cats.Eval

final case class WithId[Id, +A](id: Id, data: A) {
  def mapData[B](f: A => B): WithId[Id, B] = WithId[Id, B](id, f(data))
}

object WithId {
  def deriveEncoder [Id: Encoder, A: Encoder]: Encoder[WithId[Id, A]] = Encoder.instance { wId =>
    wId.data.asJson.asObject.get.add("id", wId.id.asJson).asJson
  }
  def deriveDecoder [Id: Decoder, A: Decoder]: Decoder[WithId[Id, A]] =
    Decoder.instance { wId =>
      for
        id <- wId.downField("id").as[Id]
        data <- wId.value.as[A]
      yield new WithId(id, data)
    }
}

type WithIdF[Id, +F[_]] = [T] =>> WithId[Id, F[T]]
type OrWithIdF[Id, F[_]] = [T] =>> OrWithId[Id, F[T]]
type OrWithId[Id, F] = WithId[Id, F] | F

object OrWithIdF:
  given [Id, Inner[_]: Functor]: Functor[OrWithIdF[Id, Inner]] with
    def map[A, B](fa: OrWithIdF[Id, Inner][A])(f: A => B): OrWithIdF[Id, Inner][B] = fa match
      case wid: WithId[Id, Inner[A]] => wid.copy(data = wid.data.map(f))
      case e: Inner[A] => e.map(f)

  given [Id, Inner[_]: Traverse]: Traverse[OrWithIdF[Id, Inner]] with
    def foldLeft[A, B](fa: OrWithIdF[Id, Inner][A], b: B)(f: (B, A) => B): B = ???
    def foldRight[A, B](fa: OrWithIdF[Id, Inner][A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = ???
    def traverse[F[_]: Applicative, A, B](fa: OrWithIdF[Id, Inner][A])(f: A => F[B]): F[OrWithIdF[Id, Inner][B]] = fa match
      case wid: WithId[Id, Inner[A]] => wid.data.traverse(f).map(data => wid.copy(data = data))
      case e: Inner[A] => e.traverse(f).widen[OrWithIdF[Id, Inner][B]]

object WithIdF:
  given [Id, Inner[_]: Functor]: Functor[WithIdF[Id, Inner]] with
    type WIDF[T] = WithIdF[Id, Inner][T]
    def map[A, B](fa: WIDF[A])(f: A => B): WIDF[B] = fa.copy(data = fa.data.map(f))

  def cata[F[_], A, Id](alg: (F[A] => A))(e: Fix[WithIdF[Id, F]])(using F: Functor[WithIdF[Id, F]]): A =
    alg(F.map(e.unfix)(cata(alg)).data)


