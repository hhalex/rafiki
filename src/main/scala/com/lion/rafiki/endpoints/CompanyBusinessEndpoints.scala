package com.lion.rafiki.endpoints

import cats.effect.Sync
import cats.syntax.all._
import com.lion.rafiki.auth.UserAuth
import com.lion.rafiki.domain.company.{Form, FormSession, FormSessionInvite}
import com.lion.rafiki.domain.{Company, ValidationError}
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

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

  def endpoints(formService: Form.Service[F],
                formSessionService: FormSession.Service[F],
                formSessionInviteService: FormSessionInvite.Service[F],
                userAuth: UserAuth[F]): HttpRoutes[F] = userAuth.authCompany {
      AuthedRoutes.of[Company.Record, F] {
        // Create form
        case req @ POST -> Root / FormRoute as companyUser =>
          val action = for {
            form <- req.req.attemptAs[Form.Create].leftMap(ValidationError.Decoding)
            result <- formService.create(form, companyUser.id.some)
          } yield result

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while creating form.")
          }
        // Update form
        case req @ PUT -> Root / FormRoute / FormIdVar(id) as companyUser =>
          val action = for {
            form <- req.req.attemptAs[Form.Create].leftMap(ValidationError.Decoding)
            result <- formService.update(form.withId(id), companyUser.id.some)
          } yield result

          action.value.flatMap {
            case Right(saved) => Ok(saved)
            case Left(err) => BadRequest(s"Error '$err' while updating form.")
          }
        // get form by id
        case GET -> Root / FormRoute / FormIdVar(id) as companyUser => {
          val action = formService.getById(id, companyUser.id.some)

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while getting form.")
          }
        }
        // delete form
        case DELETE -> Root / FormRoute / FormIdVar(id) as companyUser =>
          val action = formService.delete(id, companyUser.id.some)

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while deleting form.")
          }
        // list forms by company
        case GET -> Root / FormRoute :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) as companyUser =>
          val action = formService.listByCompany(companyUser.id, pageSize.getOrElse(10), offset.getOrElse(0))

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while listing forms.")
          }
        // Create session of a form
        case req @ POST -> Root / FormRoute / FormIdVar(formId) / SessionRoute as companyUser =>
          val action = for {
            formSession <- req.req.attemptAs[FormSession.Create].leftMap(ValidationError.Decoding)
            result <- formSessionService.create(formSession, formId, companyUser.id)
          } yield result

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while creating form session.")
          }
        // update session
        case req @ PUT -> Root / SessionRoute / FormSessionIdVar(id) as companyUser =>
          val action = for {
            form <- req.req.attemptAs[FormSession].leftMap(ValidationError.Decoding)
            result <- formSessionService.update(form.withId(id), companyUser.id)
          } yield result

          action.value.flatMap {
            case Right(saved) => Ok(saved)
            case Left(err) => BadRequest(s"Error '$err' while updating form session.")
          }
        // get session by id
        case GET -> Root / SessionRoute / FormSessionIdVar(id) as companyUser =>
          val action = formSessionService.getById(id, companyUser.id)

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while getting form session.")
          }
        // delete session
        case DELETE -> Root / SessionRoute / FormSessionIdVar(id) as companyUser =>
          val action = formSessionService.delete(id, companyUser.id)

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while deleting form session.")
          }
        // list sessions per company
        case GET -> Root / SessionRoute :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) as companyUser =>
          val action = formSessionService.listByCompany(companyUser.id, pageSize.getOrElse(10), offset.getOrElse(0))

          action.value.flatMap({
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while listing form sessions.")
          })
        // create invite on a session
        case req @ POST -> Root / SessionRoute / FormSessionIdVar(formSessionId) / InviteRoute as companyUser =>
          val action = for {
            formSessionInvite <- req.req.attemptAs[FormSessionInvite[String]].leftMap(ValidationError.Decoding)
            result <- formSessionInviteService.create(formSessionInvite, formSessionId, companyUser.id)
          } yield result

          action.value.flatMap({
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while creating form session invite.")
          })
        // list invites per session
        case GET -> Root / SessionRoute / FormSessionIdVar(formSessionId) / InviteRoute :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) as companyUser =>
          val action = (pageSize, offset) match {
            case (None, None) => formSessionInviteService.getByFormSession(formSessionId, companyUser.id)
            case _ => formSessionInviteService.listByFormSession(formSessionId, companyUser.id, pageSize.getOrElse(10), offset.getOrElse(0))
          }

          action.value.flatMap({
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while listing form session invites.")
          })
        // update invite
        case req @ PUT -> Root / InviteRoute / FormSessionInviteIdVar(inviteId) as companyUser =>
          val action = for {
            invite <- req.req.attemptAs[FormSessionInvite[String]].leftMap(ValidationError.Decoding)
            result <- formSessionInviteService.update(invite.withId(inviteId), companyUser.id)
          } yield result

          action.value.flatMap {
            case Right(saved) => Ok(saved)
            case Left(err) => BadRequest(s"Error '$err' while updating form session invite.")
          }
        // get invite by id
        case GET -> Root / InviteRoute / FormSessionInviteIdVar(inviteId) as companyUser =>
          val action = formSessionInviteService.getById(inviteId, companyUser.id)

          action.value.flatMap {
            case Right(invite) => Ok(invite)
            case Left(err) => BadRequest(s"Error '$err' while getting form session invite.")
          }
        // delete invite
        case DELETE -> Root / InviteRoute / FormSessionInviteIdVar(inviteId) as companyUser =>
          val action = formSessionInviteService.delete(inviteId, companyUser.id)

          action.value.flatMap {
            case Right(invite) => Ok(invite)
            case Left(err) => BadRequest(s"Error '$err' while deleting form session invite.")
          }
      }
    }
}
