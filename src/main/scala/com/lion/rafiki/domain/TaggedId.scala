package com.lion.rafiki.domain

import io.circe.{Decoder, Encoder}

sealed trait Tagged[+V, +T]
type @@[+V, +T] = V with Tagged[V, T]

trait TaggedId[T] {
  type Id = Long @@ T
  val tag: Long => Id = _.asInstanceOf[Id]
  val unTag: Id => Long = identity

  object Id:
    given Decoder[Id] = Decoder[Long].map(tag)
    given Encoder[Id] = Encoder[Long].contramap(unTag)
}