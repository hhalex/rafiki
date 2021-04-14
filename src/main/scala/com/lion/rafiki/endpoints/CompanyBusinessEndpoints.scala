package com.lion.rafiki.endpoints

import cats.data.EitherT
import cats.effect.Sync
import cats.syntax.all._
import com.lion.rafiki.auth.Role
import com.lion.rafiki.domain.{Company, Form, User, ValidationError}
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import tsec.authentication._
import tsec.authorization.AuthorizationInfo

class CompanyBusinessEndpoints[F[_]: Sync] extends Http4sDsl[F] {
  import CirceEntityDecoder._
  import CirceEntityEncoder._
  import Pagination._

  object FormIdVar extends IdVar[Form.Id](Form.tagSerial)

  private def createFormEndpoint(companyService: Company.Service[F], formService: Form.Service[F]): AuthEndpoint[F] = {
    case req @ POST -> Root asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- EitherT.liftF(req.request.as[Form.Create])
        result <- formService.create(form.copy(company = companyUser.id.some))
      } yield result

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => Conflict(s"Error '$err' while creating form.")
      })
  }

  private def updateFormEndpoint(companyService: Company.Service[F], formService: Form.Service[F]): AuthEndpoint[F] = {
    case req @ PUT -> Root / FormIdVar(id) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- EitherT.liftF(req.request.as[Form.Create])
        formOwner <- formService.getById(id).map(_.data.company)
        result <- {
          if (formOwner.contains(companyUser.id))
            formService.update(form.copy(company = formOwner).withId(id))
          else
            EitherT.leftT[F, Form.Record](ValidationError.NotAllowed: ValidationError)
        }
      } yield result

      action.value.flatMap {
        case Right(saved) => Ok(saved)
        case Left(err) => Conflict(s"Error '$err' while updating form.")
      }
  }

  private def getFormEndpoint(companyService: Company.Service[F], formService: Form.Service[F]): AuthEndpoint[F] = {
    case GET -> Root / FormIdVar(id) asAuthed user => {
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- formService.getById(id)
        result <- {
          if (form.data.company.contains(companyUser.id))
            EitherT.rightT[F, ValidationError](form)
          else
            EitherT.leftT[F, Form.Record](ValidationError.NotAllowed: ValidationError)
        }
      } yield result

      action.value.flatMap {
        case Right(form) => Ok(form)
        case Left(err) => Conflict(s"Error '$err' while getting form.")
      }
    }
  }

  private def deleteFormEndpoint(companyService: Company.Service[F], formService: Form.Service[F]): AuthEndpoint[F] = {
    case DELETE -> Root / FormIdVar(id) asAuthed user => {
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- formService.getById(id)
        _ <- {
          if (form.data.company.contains(companyUser.id))
            formService.delete(form.id)
          else
            EitherT.leftT[F, Unit](ValidationError.NotAllowed: ValidationError)
        }
      } yield form

      action.value.flatMap {
        case Right(form) => Ok(form)
        case Left(err) => Conflict(s"Error '$err' while deleting form.")
      }
    }
  }

  private def listFormsEndpoint(companyService: Company.Service[F], formService: Form.Service[F]): AuthEndpoint[F] = {
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(
      offset,
    ) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        result <- formService.listByCompany(companyUser.id, pageSize.getOrElse(10), offset.getOrElse(0))
      } yield result

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => Conflict(s"Error '$err' while listing forms.")
      })
  }

  def endpoints(
                 companyService: Company.Service[F],
                 formService: Form.Service[F],
                 auth: SecuredRequestHandler[F, User.Id, User.Authed, Auth],
               )(implicit A: AuthorizationInfo[F, Role, User.Authed]): HttpRoutes[F] = {

    val authEndpoints: AuthService[F] = Authentication.companyOnly(
      createFormEndpoint(companyService, formService)
          .orElse(getFormEndpoint(companyService, formService))
          .orElse(deleteFormEndpoint(companyService, formService))
          .orElse(updateFormEndpoint(companyService, formService))
          .orElse(listFormsEndpoint(companyService, formService))
      )

    Router(
      "/form" -> auth.liftService(authEndpoints)
    )
  }
}
