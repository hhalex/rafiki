package com.lion.rafiki.auth

import cats.effect.{Blocker, IO, Resource}
import com.lion.rafiki.sql.users
import doobie.{ExecutionContexts, KleisliInterpreter}
import doobie.util.transactor.{Strategy, Transactor}
import org.specs2.Specification

import java.sql.Connection


class UserStoreSpec extends Specification {
  def is = s2"""
      User Store:
        "username" should be found: $username

    """
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val transactor = Transactor[IO, Unit](
    (),
    _ => Resource.pure[IO, Connection](null),
    KleisliInterpreter[IO](Blocker.liftExecutionContext(ExecutionContexts.synchronous)).ConnectionInterpreter,
    Strategy.void
  )

  val user = UsernamePasswordCredentials("username", "password")

  val userStore = UserStore(transactor, Seq(user))

  def username = userStore.checkPassword(user).unsafeRunSync() must beSome
  //val another = userStore.checkPassword(UsernamePasswordCredentials("another", "pass")).unsafeRunSync() must beNone
}

object UserStoreSpec {
  def randomUserId: IO[users.Id] = IO.pure(users.tagSerial(1))
}