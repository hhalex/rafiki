package com.lion.rafiki.endpoints

import cats.data.EitherT
import cats.effect.Sync
import cats.syntax.all._
import com.lion.rafiki.auth.Role
import com.lion.rafiki.domain.company.{Form, FormSession}
import com.lion.rafiki.domain.{Company, User}
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
  object FormSessionIdVar extends IdVar[FormSession.Id](FormSession.tagSerial)

  private def createFormEndpoint(companyService: Company.Service[F], formService: Form.Service[F]): AuthEndpoint[F] = {
    case req @ POST -> Root asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- EitherT.liftF(req.request.as[Form.Create])
        result <- formService.create(form, companyUser.id.some)
      } yield result

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while creating form.")
      })
  }

  private def updateFormEndpoint(companyService: Company.Service[F], formService: Form.Service[F]): AuthEndpoint[F] = {
    case req @ PUT -> Root / FormIdVar(id) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- EitherT.liftF(req.request.as[Form.Create])
        result <- formService.update(form.withId(id), companyUser.id.some)
      } yield result

      action.value.flatMap {
        case Right(saved) => Ok(saved)
        case Left(err) => BadRequest(s"Error '$err' while updating form.")
      }
  }

  private def getFormEndpoint(companyService: Company.Service[F], formService: Form.Service[F]): AuthEndpoint[F] = {
    case GET -> Root / FormIdVar(id) asAuthed user => {
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- formService.getById(id, companyUser.id.some)
      } yield form

      action.value.flatMap {
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while getting form.")
      }
    }
  }

  private def deleteFormEndpoint(companyService: Company.Service[F], formService: Form.Service[F]): AuthEndpoint[F] = {
    case DELETE -> Root / FormIdVar(id) asAuthed user => {
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- formService.delete(id, companyUser.id.some)
      } yield form

      action.value.flatMap {
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while deleting form.")
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
        case Left(err) => BadRequest(s"Error '$err' while listing forms.")
      })
  }

  private def createFormSessionEndpoint(companyService: Company.Service[F], formSessionService: FormSession.Service[F]): AuthEndpoint[F] = {
    case req @ POST -> Root asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        formSession <- EitherT.liftF(req.request.as[FormSession.Create])
        result <- formSessionService.create(formSession, companyUser.id.some)
      } yield result

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while creating form session.")
      })
  }

  private def updateFormSessionEndpoint(companyService: Company.Service[F], formSessionService: FormSession.Service[F]): AuthEndpoint[F] = {
    case req @ PUT -> Root / FormSessionIdVar(id) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- EitherT.liftF(req.request.as[FormSession.Create])
        result <- formSessionService.update(form.withId(id), companyUser.id.some)
      } yield result

      action.value.flatMap {
        case Right(saved) => Ok(saved)
        case Left(err) => BadRequest(s"Error '$err' while updating form session.")
      }
  }

  private def getFormSessionEndpoint(companyService: Company.Service[F], formSessionService: FormSession.Service[F]): AuthEndpoint[F] = {
    case GET -> Root / FormSessionIdVar(id) asAuthed user => {
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- formSessionService.getById(id, companyUser.id.some)
      } yield form

      action.value.flatMap {
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while getting form session.")
      }
    }
  }

  private def deleteFormSessionEndpoint(companyService: Company.Service[F], formSessionService: FormSession.Service[F]): AuthEndpoint[F] = {
    case DELETE -> Root / FormSessionIdVar(id) asAuthed user => {
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- formSessionService.delete(id, companyUser.id.some)
      } yield form

      action.value.flatMap {
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while deleting form session.")
      }
    }
  }

  private def listFormSessionsEndpoint(companyService: Company.Service[F], formSessionService: FormSession.Service[F]): AuthEndpoint[F] = {
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(
    offset,
    ) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        result <- formSessionService.listByCompany(companyUser.id, pageSize.getOrElse(10), offset.getOrElse(0))
      } yield result

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while listing form sessions.")
      })
  }

  def endpoints(
                 companyService: Company.Service[F],
                 formService: Form.Service[F],
                 formSessionService: FormSession.Service[F],
                 auth: SecuredRequestHandler[F, User.Id, User.Authed, Auth],
               )(implicit A: AuthorizationInfo[F, Role, User.Authed]): HttpRoutes[F] = {

    val formEndpoints: AuthService[F] = Authentication.companyOnly(
      createFormEndpoint(companyService, formService)
          .orElse(getFormEndpoint(companyService, formService))
          .orElse(deleteFormEndpoint(companyService, formService))
          .orElse(updateFormEndpoint(companyService, formService))
          .orElse(listFormsEndpoint(companyService, formService))
      )

    val formSessionEndpoints: AuthService[F] = Authentication.companyOnly(
      createFormSessionEndpoint(companyService, formSessionService)
        .orElse(getFormSessionEndpoint(companyService, formSessionService))
        .orElse(deleteFormSessionEndpoint(companyService, formSessionService))
        .orElse(updateFormSessionEndpoint(companyService, formSessionService))
        .orElse(listFormSessionsEndpoint(companyService, formSessionService))
    )

    Router(
      "/form" -> auth.liftService(formEndpoints),
      "/formsession" -> auth.liftService(formSessionEndpoints),
    )
  }
}
