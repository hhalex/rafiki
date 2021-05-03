package com.lion.rafiki.domain

import io.circe.{Decoder, Encoder}
trait TaggedId {
  case class Id(value: Long)
  def tag(t: Long): Id = Id(t)
  def unTag(id: Id): Long = id.value

  implicit val taggedIdDecoder: Decoder[Id] = Decoder[Long].map(tag)
  implicit val taggedIdEncoder: Encoder[Id] = Encoder[Long].contramap(unTag)
}
