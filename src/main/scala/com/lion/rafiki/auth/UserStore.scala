package com.lion.rafiki.auth

import cats.implicits._
import cats.{Applicative, Monad}
import cats.data.OptionT
import cats.implicits.{catsSyntaxApplicativeId, toTraverseOps}
import tsec.authentication.IdentityStore
import tsec.passwordhashers.jca.BCrypt
import com.lion.rafiki.domain.User
import tsec.passwordhashers.PasswordHasher

case class UserStore[F[_]](
                      identityStore: IdentityStore[F, User.Id, User.Authed],
                      checkPassword: UsernamePasswordCredentials => F[Option[User.Authed]]
                    )

object UserStore {

  // A hot user is a user generated at startup time.
  def newHotUser[F[_]: Applicative](username: String, password: String)(implicit P: PasswordHasher[F, BCrypt]): F[User.Authed] = {
    Applicative[F].map2(
      User.tagSerial(username.hashCode).pure[F],
      BCrypt.hashpw[F](password)
    )(User.Authed(_, username, _))
  }

  def apply[F[_]: Monad](userService: User.Service[F], hot_users: Seq[UsernamePasswordCredentials])(implicit P: PasswordHasher[F, BCrypt]): UserStore[F] = {
    def userList(): F[List[User.Authed]] = hot_users
      .map(u => newHotUser[F](u.username, u.password))
      .toList
      .sequence

    new UserStore(
      // First we check in the list of hot users, then we search the database if we don't find anything
      (id: User.Id) => OptionT(userList().map(_.find(_.id == id)))
        .orElseF(userService.getById(id).map(u => User.Authed(u.id, u.data.username, u.data.password)).toOption.value),
      // We validate first against the hot users's list before checking against the database
      creds => for {
        maybeUser <- OptionT(userList().map(_.find(_.username == creds.username)))
          .orElseF(userService.getByName(creds.username).map(u => User.Authed(u.id, u.data.username, u.data.password)).toOption.value)
          .value
        validPassword <- maybeUser match {
          case Some(u) => BCrypt.checkpwBool[F](creds.password, u.password)
          case None => false.pure[F]
        }
      } yield if (validPassword) maybeUser else None
    )
  }
}
