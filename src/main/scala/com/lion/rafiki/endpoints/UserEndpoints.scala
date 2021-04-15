package com.lion.rafiki.endpoints

import cats.effect.Sync
import cats.syntax.all._
import com.lion.rafiki.auth.{Role, UserStore, UsernamePasswordCredentials}
import com.lion.rafiki.domain.{User}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.Decoder
import org.http4s.{EntityDecoder, HttpRoutes, Response, Status}
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import tsec.authentication.{Authenticator, SecuredRequestHandler}
import tsec.authorization.AuthorizationInfo
import tsec.authentication._

class UserEndpoints[F[_]: Sync] extends Http4sDsl[F] {
  import Pagination._, org.http4s.circe.CirceEntityDecoder._, org.http4s.circe.CirceEntityEncoder._

  private def loginEndpoint(auth: Authenticator[F, User.Id, User.Authed, Auth],
                 checkPassword: UsernamePasswordCredentials => F[Option[User.Authed]]): HttpRoutes[F] = {
    implicit val loginUserDecoder: Decoder[UsernamePasswordCredentials] = deriveDecoder
    implicit val entityLoginUserDecoder: EntityDecoder[F, UsernamePasswordCredentials] =
      jsonOf[F, UsernamePasswordCredentials]
    HttpRoutes.of[F] {
      case req @ POST -> Root =>
        (for {
          user <- req.as[UsernamePasswordCredentials]
          userOpt <- checkPassword(user)
        } yield userOpt).flatMap {
          case Some(user) => auth.create(user.id).map(auth.embed(Response[F](Status.Ok).withEntity(user.role), _))
          case None => Response[F](Status.Unauthorized).pure[F]
        }
    }
  }

  private def updateEndpoint(userService: User.Service[F]): AuthEndpoint[F] = {
    case req @ PUT -> Root asAuthed _ =>
      val action = for {
        user <- req.request.as[User.Update]
        result <- userService.update(user).value
      } yield result

      action.flatMap {
        case Right(saved) => Ok(saved)
        case Left(err) => BadRequest(s"Error '$err' while updating user.")
      }
  }

  private def listEndpoint(userService: User.Service[F]): AuthEndpoint[F] = {
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(
    offset,
    ) asAuthed _ =>
      userService.list(pageSize.getOrElse(10), offset.getOrElse(0)).value.flatMap {
        case Right(list) => Ok(list)
        case Left(err) => BadRequest(s"Error '$err' while listing users.")
      }
  }

  private def searchByNameEndpoint(userService: User.Service[F]): AuthEndpoint[F] = {
    case GET -> Root / userName asAuthed _ =>
      userService.getByName(userName).value.flatMap {
        case Right(found) => Ok(found)
        case Left(err) => BadRequest(s"Error '$err' while searching user by name.")
      }
  }

  private def deleteUserEndpoint(userService: User.Service[F]): AuthEndpoint[F] = {
    case DELETE -> Root / userName asAuthed _ =>
      userService.deleteByUserName(userName).value.flatMap {
        case Right(_) => Ok()
        case Left(err) => BadRequest(s"Error '$err' while deleting user.")
      }
  }

  def endpoints(
                 userService: User.Service[F],
                 userStore: UserStore[F],
                 auth: SecuredRequestHandler[F, User.Id, User.Authed, Auth],
               )(implicit A: AuthorizationInfo[F, Role, User.Authed]): HttpRoutes[F] = {

    val authEndpoints: AuthService[F] =
      Authentication.adminOnly {
        updateEndpoint(userService)
          .orElse(listEndpoint(userService))
          .orElse(searchByNameEndpoint(userService))
          .orElse(deleteUserEndpoint(userService))
      }


    Router(
      "/login" -> loginEndpoint(auth.authenticator, userStore.checkPassword),
      "/user" -> auth.liftService(authEndpoints)
    )
  }
}
