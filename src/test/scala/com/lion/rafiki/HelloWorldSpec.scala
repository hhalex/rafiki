package com.lion.rafiki


import cats.effect.IO
import com.lion.rafiki.auth.UserStore.{User, UserId}
import io.chrisdavenport.fuuid.FUUID
import org.http4s._
import org.http4s.implicits._
import org.specs2.matcher.MatchResult
import tsec.authentication.{SecuredRequest, TSecBearerToken}
import tsec.common.SecureRandomId
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

import java.time.Instant
import java.util.UUID

class HelloWorldSpec extends org.specs2.mutable.Specification {

  "HelloWorld" >> {
    "return 200" >> {
      uriReturns200()
    }
    "return hello world" >> {
      uriReturnsHelloWorld()
    }
  }

  private[this] val retHelloWorld: Response[IO] = {
    val userid = FUUID.fromUUID(UUID.randomUUID()).asInstanceOf[UserId]
    val getHW = SecuredRequest[IO, User, TSecBearerToken[UserId]](
      Request[IO](Method.GET, uri"/hello/world"),
      User(userid, "world", PasswordHash[BCrypt]("mdp")),
      TSecBearerToken(SecureRandomId(""), userid, Instant.MAX, None)
    )
    val helloWorld = HelloWorld.impl[IO]
    Routes.helloWorldRoutes(helloWorld).orNotFound(getHW).unsafeRunSync()
  }

  private[this] def uriReturns200(): MatchResult[Status] =
    retHelloWorld.status must beEqualTo(Status.Ok)

  private[this] def uriReturnsHelloWorld(): MatchResult[String] =
    retHelloWorld.as[String].unsafeRunSync() must beEqualTo("{\"message\":\"Hello, world\"}")
}
