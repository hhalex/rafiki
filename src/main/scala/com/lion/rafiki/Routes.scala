package com.lion.rafiki

import cats.effect.{Blocker, ContextShift, IO}
import com.lion.rafiki.Auth.{User, UserId}
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.Http4sDsl
import tsec.authentication.{TSecAuthService, TSecBearerToken, asAuthed}

object Routes {
  val dsl = new Http4sDsl[IO]{}
  import dsl._
  def jokeRoutes(J: Jokes[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "joke" =>
      for {
        joke <- J.get
        resp <- Ok(joke)
      } yield resp
  }

  def helloWorldRoutes(H: HelloWorld[IO]) = TSecAuthService[User, TSecBearerToken[UserId], IO] {
    case GET -> Root / "hello" / name asAuthed user =>
      for {
        greeting <- H.hello(HelloWorld.Name(user.username))
        resp <- Ok(greeting)
      } yield resp
  }

  def uiRoutes(implicit b: Blocker, c: ContextShift[IO]) = HttpRoutes.of[IO] {
    case request@GET -> path if path.startsWith(Path("static"))  =>
        StaticFile.fromResource(path.toString, b, Some(request))
          .getOrElseF(NotFound())
    case request@GET -> Root  => StaticFile.fromResource("/index.html", b, Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
    case request@GET -> Root / "manifest.json"  => StaticFile.fromResource("/manifest.json", b, Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
    case request@GET -> Root / "robots.txt"  => StaticFile.fromResource("/robots.txt", b, Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
    case request@GET -> path if path.toString.endsWith(".png") || path.toString.endsWith(".ico")  => StaticFile.fromResource(path.toString, b, Some(request))
      .getOrElseF(NotFound())
  }
}
