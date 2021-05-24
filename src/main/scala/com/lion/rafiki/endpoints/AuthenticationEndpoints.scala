package com.lion.rafiki.endpoints

import cats.effect.Async
import cats.implicits._
import com.lion.rafiki.auth.{UserAuth, UserCredentials}
import com.lion.rafiki.domain.ValidationError._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpRoutes, Response, Status}
import com.lion.rafiki.auth.AuthError

class AuthenticationEndpoints[F[_]: Async] extends Http4sDsl[F]  {
  given Decoder[UserCredentials] = deriveDecoder
  given EntityDecoder[F, UserCredentials] = jsonOf[F, UserCredentials]
  def endpoints(userAuth: UserAuth[F], clock: java.time.Clock): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST -> Root / "login" =>
        val action = for
          userCreds <- req.attemptAs[UserCredentials].leftWiden
          user <- userAuth.validateCredentials(userCreds)
          userRole <- userAuth.role(user).leftWiden
        yield (user, userRole)

        action.value.flatMap {
          case Right(user) =>
            Ok(user._2).map(userAuth.embedAuthHeader(_, user._1, clock.millis().toString))
          case Left(err: AuthError) => Response[F](Status.Unauthorized).withEntity(err.toString).pure[F]
          case Left(err) => Response[F](Status.BadRequest).withEntity(err.toString).pure[F]
        }
    }
}
