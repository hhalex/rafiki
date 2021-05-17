package com.lion.rafiki.domain

import io.circe.{Decoder, Encoder, Json, HCursor}
import cats.Functor
import cats.Traverse
import cats.syntax.all._

case class Fix[F[_]](unfix: F[Fix[F]])

object Fix:
  def deriveEncoder[F[_]: Functor](using e: Encoder[F[Json]]): Encoder[Fix[F]] = Encoder.instance { cata[F, Json](e.apply) _ }
  def deriveDecoder[F[_]: Traverse](using d: Decoder[F[Json]], f: Functor[Decoder.Result]): Decoder[Fix[F]] =
    Decoder.instance { hcursor => anaResult[F, Json](d.decodeJson)(hcursor.value) }
def cata[F[_]: Functor, A](alg: (F[A] => A))(e: Fix[F]): A =
  alg(e.unfix.map(cata(alg)))

def ana[F[_]: Functor, A](coalg: (A => F[A]))(a: A): Fix[F] =
  Fix(coalg(a).map(ana(coalg)))

def anaResult[F[_]: Traverse, A](coalg: A => Decoder.Result[F[A]])(a: A): Decoder.Result[Fix[F]] =
  for
    inner <- coalg(a)
    result <- inner.traverse(anaResult[F, A](coalg))
  yield
    Fix(result)