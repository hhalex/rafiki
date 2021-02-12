package com.lion.rafiki.auth

import cats.Applicative
import cats.data.OptionT
import cats.effect.IO
import cats.implicits.toTraverseOps
import com.lion.rafiki.sql.users
import doobie.util.transactor.Transactor
import tsec.authentication.IdentityStore
import tsec.passwordhashers.jca.BCrypt
import doobie.implicits._

case class UserStore(
                      identityStore: IdentityStore[IO, users.Id, users.T],
                      checkPassword: UsernamePasswordCredentials => IO[Option[users.T]]
                    )

object UserStore {

  // A hot user is a user generated at startup time.
  def newHotUser(username: String, password: String): IO[users.T] = {
    Applicative[IO].map2(
      IO.pure(users.tagSerial(username.hashCode)),
      BCrypt.hashpw[IO](password)
    )(users.T(_, None, None, username, _))
  }

  def apply(xa: Transactor.Aux[IO, Unit], hot_users: Seq[UsernamePasswordCredentials]): UserStore = {
    def userList() = hot_users
      .map(u => newHotUser(u.username, u.password))
      .toList
      .sequence

    new UserStore(
      // First we check in the list of hot users, then we search the database if we don't find anything
      (id: users.Id) => OptionT(userList().map(_.find(_.id == id)))
        .orElseF(users.getById(id).transact(xa).map(Some(_))),
      // We validate first against the hot users's list before checking against the database
      creds => for {
        maybeUser <- OptionT(userList().map(_.find(_.username == creds.username)))
          .orElseF(users.getByEmail(creds.username).transact(xa))
          .value
        validPassword <- maybeUser match {
          case Some(u) => BCrypt.checkpwBool[IO](creds.password, u.password)
          case None => IO.pure(false)
        }
      } yield if (validPassword) maybeUser else None
    )
  }


}
