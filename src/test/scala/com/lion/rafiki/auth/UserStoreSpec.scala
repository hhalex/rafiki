package com.lion.rafiki.auth

import cats.effect.{IO, Resource}
import com.lion.rafiki.domain.User
import doobie.{ExecutionContexts, KleisliInterpreter}
import doobie.util.transactor.{Strategy, Transactor}
import org.specs2.Specification

import java.sql.Connection

/*
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

  val

  val userStore: UserStore[IO] = UserStore[IO](transactor, Seq(user))

  def username() = userStore.checkPassword(user).unsafeRunSync() must beSome
  //val another = userStore.checkPassword(UsernamePasswordCredentials("another", "pass")).unsafeRunSync() must beNone
}

object UserStoreSpec {
  def randomUserId: IO[User.Id] = IO.pure(User.tagSerial(1))
}*/
