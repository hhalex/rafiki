package com.lion.rafiki

import cats.effect.{Blocker, ContextShift, IO}
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.Http4sDsl

object Routes {
  val dsl = new Http4sDsl[IO]{}
  import dsl._

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
