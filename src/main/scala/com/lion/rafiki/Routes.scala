package com.lion.rafiki

import cats.effect.{Blocker, ContextShift, IO}
import com.lion.rafiki.Auth.{User, UserId, UsernamePasswordCredentials}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.circe.jsonOf
import org.http4s.{EntityDecoder, HttpRoutes, Response, StaticFile, Status}
import org.http4s.dsl.Http4sDsl
import tsec.authentication.{BearerTokenAuthenticator, TSecAuthService, TSecBearerToken, asAuthed}

object Routes {
  val dsl = new Http4sDsl[IO]{}
  import dsl._

  def loginRoute(
                  auth: BearerTokenAuthenticator[IO, UserId, User],
                  checkPassword: UsernamePasswordCredentials => IO[Option[User]]): HttpRoutes[IO] = {
    implicit val loginUserDecoder: Decoder[UsernamePasswordCredentials] = deriveDecoder
    implicit val entityLoginUserDecoder: EntityDecoder[IO, UsernamePasswordCredentials] =
      jsonOf[IO, UsernamePasswordCredentials]
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "login" =>
        (for {
          user <- req.as[UsernamePasswordCredentials]
          userOpt <- checkPassword(user)
        } yield userOpt).flatMap {
          case Some(user) => auth.create(user.id).map(auth.embed(Response(Status.Ok), _))
          case None => IO.pure(Response[IO](Status.Unauthorized))
        }
    }
  }

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
