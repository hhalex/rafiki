package com.lion.rafiki.auth

import cats.data.OptionT
import cats.effect.IO
import cats.effect.concurrent.Ref
import com.lion.rafiki.auth.UserStore.UserId
import tsec.authentication.{BackingStore, TSecBearerToken}
import tsec.common.SecureRandomId

object TokenStore {
  type BS = BackingStore[IO, SecureRandomId, TSecBearerToken[UserId]]
  def apply(ref: Ref[IO, Map[SecureRandomId, TSecBearerToken[UserId]]]): BS =
    new BS {
      override def put(elem: TSecBearerToken[UserId]): IO[TSecBearerToken[UserId]] =
        ref.modify(store => (store + (elem.id -> elem), elem))

      override def update(elem: TSecBearerToken[UserId]): IO[TSecBearerToken[UserId]] = put(elem)

      override def delete(id: SecureRandomId): IO[Unit] = ref.modify(store => (store - id, ()))

      override def get(id: SecureRandomId): OptionT[IO, TSecBearerToken[UserId]] =
        OptionT(ref.get.map(_.get(id)))
    }

  val empty: IO[BS] = for {
    tokens <- Ref.of[IO, Map[SecureRandomId, TSecBearerToken[UserId]]](Map.empty)
  } yield TokenStore(tokens)
}
