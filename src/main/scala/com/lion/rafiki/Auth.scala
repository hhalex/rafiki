package com.lion.rafiki

import cats.{Applicative, FlatMap, Functor}
import cats.data.OptionT
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.effect.{IO, Sync}
import cats.effect.concurrent.Ref
import io.chrisdavenport.fuuid.FUUID
import shapeless.tag.@@
import tsec.authentication.{BackingStore, BearerTokenAuthenticator, IdentityStore, TSecBearerToken, TSecTokenSettings}
import tsec.common.SecureRandomId
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

import scala.concurrent.duration.DurationInt

object Auth {
  type UserId = FUUID @@ User // shapeless.tag.@@

  case class User(id: UserId,
                  username: String,
                  password: PasswordHash[BCrypt])

  def userStore(users: Ref[IO, Map[UserId, User]]) =
    new IdentityStore[IO, UserId, User] {
      override def get(id: UserId) = OptionT(users.get.map(_.get(id)))
    }

  def tokenStore(ref: Ref[IO, Map[SecureRandomId, TSecBearerToken[UserId]]]) =
    new BackingStore[IO, SecureRandomId, TSecBearerToken[UserId]] {
      override def put(elem: TSecBearerToken[UserId]) =
        ref.modify(store => (store + (elem.id -> elem), elem))
      override def update(elem: TSecBearerToken[UserId]) = put(elem)
      override def delete(id: SecureRandomId) = ref.modify(store => (store - id, ()))
      override def get(id: SecureRandomId) = OptionT(ref.get.map(_.get(id)))
    }

  def authenticator(): IO[BearerTokenAuthenticator[IO, UserId, User]] = for {
    tokens <- Ref.of[IO, Map[SecureRandomId, TSecBearerToken[UserId]]](Map.empty)
    users <- Ref.of[IO, Map[UserId, User]](Map.empty)
  } yield BearerTokenAuthenticator(
    tokenStore(tokens),
    userStore(users),
    TSecTokenSettings(
      expiryDuration = 10.minutes,
      maxIdle = None
    ))
}
