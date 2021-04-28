package com.lion.rafiki.endpoints

import cats.data.EitherT
import cats.effect.Sync
import cats.syntax.all._
import com.lion.rafiki.auth.Role
import com.lion.rafiki.domain.company.{Form, FormSession, FormSessionInvite}
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

  val FormRoute = "form"
  val SessionRoute = "session"
  val InviteRoute = "invite"

  object FormIdVar extends IdVar[Form.Id](Form.tagSerial)
  object FormSessionIdVar extends IdVar[FormSession.Id](FormSession.tagSerial)
  object FormSessionInviteIdVar extends IdVar[FormSessionInvite.Id](FormSessionInvite.tagSerial)

  private def formEndpoints(companyService: Company.Service[F], formService: Form.Service[F], formSessionService: FormSession.Service[F]): AuthEndpoint[F] = {
        // Create form
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
      // Update form
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
      // get form by id
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
      // delete form
    case DELETE -> Root / FormIdVar(id) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- formService.delete(id, companyUser.id.some)
      } yield form

      action.value.flatMap {
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while deleting form.")
      }
      // list forms by company
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        result <- formService.listByCompany(companyUser.id, pageSize.getOrElse(10), offset.getOrElse(0))
      } yield result

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while listing forms.")
      })
      // Create session of a form
    case req @ POST -> Root / FormIdVar(formId) / "session" asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        formSession <- EitherT.liftF(req.request.as[FormSession.Create])
        result <- formSessionService.create(formSession, formId, companyUser.id)
      } yield result

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while creating form session.")
      })
  }

  private def formSessionEndpoint(companyService: Company.Service[F], formSessionService: FormSession.Service[F], formSessionInviteService: FormSessionInvite.Service[F]): AuthEndpoint[F] = {
        // update session
    case req @ PUT -> Root / FormSessionIdVar(id) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- EitherT.liftF(req.request.as[FormSession.Create])
        result <- formSessionService.update(form.withId(id), companyUser.id)
      } yield result

      action.value.flatMap {
        case Right(saved) => Ok(saved)
        case Left(err) => BadRequest(s"Error '$err' while updating form session.")
      }
      // get session by id
    case GET -> Root / FormSessionIdVar(id) asAuthed user => {
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- formSessionService.getById(id, companyUser.id)
      } yield form

      action.value.flatMap {
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while getting form session.")
      }
    }
      // delete session
    case DELETE -> Root / FormSessionIdVar(id) asAuthed user => {
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        form <- formSessionService.delete(id, companyUser.id)
      } yield form

      action.value.flatMap {
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while deleting form session.")
      }
    }
      // list sessions per company
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        result <- formSessionService.listByCompany(companyUser.id, pageSize.getOrElse(10), offset.getOrElse(0))
      } yield result

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while listing form sessions.")
      })
      // create invite on a session
    case req @ POST -> Root / FormSessionIdVar(formSessionId) / InviteRoute asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        formSession <- EitherT.liftF(req.request.as[FormSessionInvite.Create])
        result <- formSessionInviteService.create(formSession, formSessionId, companyUser.id)
      } yield result

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while creating form session invite.")
      })
      // list invites per session
    case GET -> Root / FormSessionIdVar(formSessionId) / InviteRoute :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        invites <- formSessionInviteService.listByFormSession(formSessionId, pageSize.getOrElse(10), offset.getOrElse(0))
      } yield invites

      action.value.flatMap({
        case Right(form) => Ok(form)
        case Left(err) => BadRequest(s"Error '$err' while listing form session invites.")
      })
  }

  private def formSessionInviteEndpoint(companyService: Company.Service[F], formSessionInviteService: FormSessionInvite.Service[F]): AuthEndpoint[F] = {
    // update invite
    case req @ PUT -> Root / FormSessionInviteIdVar(inviteId) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        invite <- EitherT.liftF(req.request.as[FormSessionInvite.Create])
        result <- formSessionInviteService.update(invite.withId(inviteId), companyUser.id)
      } yield result

      action.value.flatMap {
        case Right(saved) => Ok(saved)
        case Left(err) => BadRequest(s"Error '$err' while updating form session invite.")
      }
      // get invite by id
    case GET -> Root / FormSessionInviteIdVar(inviteId) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        invite <- formSessionInviteService.getById(inviteId, companyUser.id)
      } yield invite

      action.value.flatMap {
        case Right(invite) => Ok(invite)
        case Left(err) => BadRequest(s"Error '$err' while getting form session invite.")
      }
      // delete invite
    case DELETE -> Root / FormSessionInviteIdVar(inviteId) asAuthed user =>
      val action = for {
        companyUser <- companyService.getFromUser(user.id)
        invite <- formSessionInviteService.delete(inviteId, companyUser.id)
      } yield invite

      action.value.flatMap {
        case Right(invite) => Ok(invite)
        case Left(err) => BadRequest(s"Error '$err' while deleting form session invite.")
      }
  }

  def endpoints(
                 companyService: Company.Service[F],
                 formService: Form.Service[F],
                 formSessionService: FormSession.Service[F],
                 formSessionInviteService: FormSessionInvite.Service[F],
                 auth: SecuredRequestHandler[F, User.Id, User.Authed, Auth],
               )(implicit A: AuthorizationInfo[F, Role, User.Authed]): HttpRoutes[F] = {

    Router(
      FormRoute -> auth.liftService(Authentication.companyOnly(formEndpoints(companyService, formService, formSessionService))),
      SessionRoute -> auth.liftService(Authentication.companyOnly(formSessionEndpoint(companyService, formSessionService, formSessionInviteService))),
      InviteRoute -> auth.liftService(Authentication.companyOnly(formSessionInviteEndpoint(companyService, formSessionInviteService)))
    )
  }
}
