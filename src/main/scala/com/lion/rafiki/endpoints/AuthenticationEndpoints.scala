package com.lion.rafiki.endpoints

import cats.effect.Sync
import cats.implicits._
import com.lion.rafiki.auth.{UserAuth, UserCredentials}
import com.lion.rafiki.domain.ValidationError
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpRoutes, Response, Status}

class AuthenticationEndpoints[F[_]: Sync] extends Http4sDsl[F]  {
  implicit val loginUserDecoder: Decoder[UserCredentials] = deriveDecoder
  implicit val entityLoginUserDecoder: EntityDecoder[F, UserCredentials] = jsonOf[F, UserCredentials]
  def endpoints(userAuth: UserAuth[F], clock: java.time.Clock): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST -> Root / "login" =>
        val action= for {
          userCreds <- req.attemptAs[UserCredentials].leftMap(ValidationError.Decoding)
          user <- userAuth.validateCredentials(userCreds).leftMap[ValidationError](ValidationError.Auth)
          userRole <- userAuth.role(user).leftMap[ValidationError](ValidationError.Auth)
        } yield (user, userRole)

        action.value.flatMap {
          case Right(user) =>
            Ok(user._2).map(userAuth.embedAuthHeader(_, user._1, clock.millis().toString))
          case Left(ValidationError.Auth(err)) => Response[F](Status.Unauthorized).withEntity(err.toString).pure[F]
          case Left(err) => Response[F](Status.BadRequest).withEntity(err.toString).pure[F]
        }
    }
}
