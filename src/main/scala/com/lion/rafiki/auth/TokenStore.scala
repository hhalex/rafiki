package com.lion.rafiki.auth

import cats.data.OptionT
import cats.effect.IO
import cats.effect.concurrent.Ref
import com.lion.rafiki.sql.users
import tsec.authentication.{BackingStore, TSecBearerToken}
import tsec.common.SecureRandomId

object TokenStore {
  type BS = BackingStore[IO, SecureRandomId, TSecBearerToken[users.Id]]
  def apply(ref: Ref[IO, Map[SecureRandomId, TSecBearerToken[users.Id]]]): BS =
    new BS {
      override def put(elem: TSecBearerToken[users.Id]): IO[TSecBearerToken[users.Id]] =
        ref.modify(store => (store + (elem.id -> elem), elem))

      override def update(elem: TSecBearerToken[users.Id]): IO[TSecBearerToken[users.Id]] = put(elem)

      override def delete(id: SecureRandomId): IO[Unit] = ref.modify(store => (store - id, ()))

      override def get(id: SecureRandomId): OptionT[IO, TSecBearerToken[users.Id]] =
        OptionT(ref.get.map(_.get(id)))
    }

  val empty: IO[BS] = for {
    tokens <- Ref.of[IO, Map[SecureRandomId, TSecBearerToken[users.Id]]](Map.empty)
  } yield TokenStore(tokens)
}
