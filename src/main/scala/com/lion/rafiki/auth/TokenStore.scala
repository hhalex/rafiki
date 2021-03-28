package com.lion.rafiki.auth

import cats.data.OptionT
import cats.effect.IO
import cats.effect.concurrent.Ref
import com.lion.rafiki.domain.User
import com.lion.rafiki.endpoints.Auth
import tsec.authentication.{BackingStore, TSecBearerToken}
import tsec.common.SecureRandomId

object TokenStore {
  type BS = BackingStore[IO, SecureRandomId, Auth]
  def apply(ref: Ref[IO, Map[SecureRandomId, Auth]]): BS =
    new BS {
      override def put(elem: Auth): IO[Auth] =
        ref.modify(store => (store + (elem.id -> elem), elem))

      override def update(elem: TSecBearerToken[User.Id]): IO[Auth] = put(elem)

      override def delete(id: SecureRandomId): IO[Unit] = ref.modify(store => (store - id, ()))

      override def get(id: SecureRandomId): OptionT[IO, Auth] =
        OptionT(ref.get.map(_.get(id)))
    }

  val empty: IO[BS] = for {
    tokens <- Ref.of[IO, Map[SecureRandomId, Auth]](Map.empty)
  } yield TokenStore(tokens)
}
