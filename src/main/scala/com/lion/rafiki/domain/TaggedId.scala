package com.lion.rafiki.domain

import io.circe.{Decoder, Encoder}
import shapeless.tag
import shapeless.tag.@@

trait TaggedId[T] {
  type Id = Long @@ T
  val tagSerial = tag[T](_: Long)

  implicit val formSessionIdDecoder: Decoder[Id] = Decoder[Long].map(tagSerial)
  implicit val formSessionIdEncoder: Encoder[Id] = Encoder[Long].contramap(_.asInstanceOf[Long])
}
