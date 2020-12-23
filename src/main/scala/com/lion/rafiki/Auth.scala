package com.lion.rafiki

import cats.Applicative
import cats.data.OptionT
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits.{catsSyntaxFoldOps, toTraverseOps}
import io.chrisdavenport.fuuid.FUUID
import shapeless.tag.{@@, Tagger}
import tsec.authentication.{BackingStore, BearerTokenAuthenticator, IdentityStore, TSecBearerToken, TSecTokenSettings}
import tsec.common.SecureRandomId
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

import scala.concurrent.duration.DurationInt

object Auth {
  type UserId = FUUID @@ User // shapeless.tag.@@
  val tagFUUIDAsUserId = {
    val tagger = new Tagger[User]
    (t: FUUID) => tagger(t)
  }
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

  case class UsernamePasswordCredentials(username: String, password: String)
  case class UserStore(
                        identityStore: IdentityStore[IO, UserId, User],
                        checkPassword: UsernamePasswordCredentials => IO[Option[User]]
                      )

  object UserStore {

    def newUser(username: String, password: String): IO[User] =
      Applicative[IO].map2(
        FUUID.randomFUUID[IO].map(tagFUUIDAsUserId),
        BCrypt.hashpw[IO](password)
      )(User(_, username, _))

    def validateUser(credentials: UsernamePasswordCredentials)(
      users: List[User]): IO[Option[User]] =
      users.findM(
        user =>
          BCrypt
            .checkpwBool[IO](credentials.password, user.password)
            .map(_ && credentials.username == user.username),
      )

    def apply(user: UsernamePasswordCredentials, users: UsernamePasswordCredentials*): IO[UserStore] =
      for {
        userList <- (user +: users)
          .map(u => UserStore.newUser(u.username, u.password))
          .toList
          .sequence
        users <- Ref.of[IO, Map[UserId, User]](userList.map(u => u.id -> u).toMap)
      } yield
        new UserStore(
          (id: UserId) => OptionT(users.get.map(_.get(id))),
          usr => users.get.map(_.values.toList).flatMap(validateUser(usr)(_))
        )
  }
}
