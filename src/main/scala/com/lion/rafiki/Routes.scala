package com.lion.rafiki

import cats.effect.{IO, Sync}
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.Http4sDsl

object Routes {
  val dsl = new Http4sDsl[IO]{}
  import dsl._

  def uiRoutes(implicit c: Sync[IO]) = HttpRoutes.of[IO] {
    case request@GET -> path if path.startsWith(Path.unsafeFromString("static"))  =>
        StaticFile.fromResource(path.toString, Some(request))
          .getOrElseF(NotFound())
    case request@GET -> Root  => StaticFile.fromResource("/index.html", Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
    case request@GET -> Root / "manifest.json"  => StaticFile.fromResource("/manifest.json", Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
    case request@GET -> Root / "robots.txt"  => StaticFile.fromResource("/robots.txt", Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
    case request@GET -> path if path.toString.endsWith(".png") || path.toString.endsWith(".ico")  => StaticFile.fromResource(path.toString, Some(request))
      .getOrElseF(NotFound())
  }
}
