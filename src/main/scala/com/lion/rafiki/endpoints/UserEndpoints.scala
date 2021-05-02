package com.lion.rafiki.endpoints

import cats.effect.Sync
import cats.syntax.all._
import com.lion.rafiki.auth.{UserAuth, UserCredentials}
import com.lion.rafiki.domain.{User, ValidationError}
import com.lion.rafiki.domain.company.Form
import org.http4s.{AuthedRoutes, EntityDecoder, HttpRoutes, Response, Status}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class UserEndpoints[F[_]: Sync] extends Http4sDsl[F] {
  import Pagination._, org.http4s.circe.CirceEntityDecoder._, org.http4s.circe.CirceEntityEncoder._

  val UserRoute = "user"

  def endpoints(userService: User.Service[F], userAuth: UserAuth[F]): HttpRoutes[F] =
    userAuth.authAdmin {
      AuthedRoutes.of[User.Authed, F] {
        case req @ PUT -> Root as _ =>
          val action = for {
            user <- req.req.attemptAs[User.Update].leftMap(ValidationError.Decoding)
            result <- userService.update(user)
          } yield result

          action.value.flatMap {
            case Right(saved) => Ok(saved)
            case Left(err) => BadRequest(s"Error '$err' while updating user.")
          }
        case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) as _ =>
          // TODO: check optional parameters to use 'getWithUser' (without pagination)
          userService.list(pageSize.getOrElse(10), offset.getOrElse(0)).value.flatMap {
            case Right(list) => Ok(list)
            case Left(err) => BadRequest(s"Error '$err' while listing users.")
          }
        case DELETE -> Root / userName as _ =>
          userService.deleteByUserName(userName).value.flatMap {
            case Right(_) => Ok()
            case Left(err) => BadRequest(s"Error '$err' while deleting user.")
          }
      }
  }
}
