package com.lion.rafiki.domain

import io.circe.{Decoder, Encoder}
import io.circe.syntax._

final case class WithId[Id, +A](id: Id, data: A) {
  def mapData[B](f: A => B): WithId[Id, B] = WithId[Id, B](id, f(data))
}

object WithId {
  def encoder[Id: Encoder, A: Encoder]: Encoder[WithId[Id, A]] =
    Encoder.instance { wId =>
      wId.data.asJson.asObject.get.add("id", wId.id.asJson).asJson
    }

  def decoder[Id: Decoder, A: Decoder]: Decoder[WithId[Id, A]] =
    Decoder.instance { wId =>
      for {
        id <- wId.downField("id").as[Id]
        data <- wId.value.as[A]
      } yield new WithId(id, data)
    }
}
