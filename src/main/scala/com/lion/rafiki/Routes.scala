package com.lion.rafiki

import java.io.File

import cats.effect.{Blocker, ContextShift, Sync}
import cats.implicits._
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.staticcontent._

object Routes {

  def jokeRoutes[F[_]: Sync](J: Jokes[F]): HttpRoutes[F] = {
      val dsl = new Http4sDsl[F]{}
      import dsl._
      HttpRoutes.of[F] {
        case GET -> Root / "joke" =>
          for {
            joke <- J.get
            resp <- Ok(joke)
          } yield resp
    }
  }

  def helloWorldRoutes[F[_]: Sync](H: HelloWorld[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "hello" / name =>
        for {
          greeting <- H.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp
    }
  }

  def uiRoutes[F[_]: Sync](implicit b: Blocker, c: ContextShift[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
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
}
