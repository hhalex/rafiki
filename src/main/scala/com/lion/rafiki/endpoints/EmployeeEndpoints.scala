package com.lion.rafiki.endpoints

import cats.effect.{Async, Sync}
import cats.Applicative
import cats.syntax.all._
import com.lion.rafiki.auth.UserAuth
import com.lion.rafiki.domain.company.{Form, FormSession, SessionInvite}
import com.lion.rafiki.domain.{User, ValidationError, TaggedId}
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.EntityEncoder
import com.lion.rafiki.domain.company.InviteAnswer

class EmployeeEndpoints[F[_]: Async] extends Http4sDsl[F] {

  import CirceEntityDecoder._
  import CirceEntityEncoder._
  import Pagination._

  val AnswerRoute = "session"
  val InviteRoute = "invite"

  // SessionInvites and SessionAnswer have exactly the same id
  object SessionInviteIdVar extends IdVar[SessionInvite.Id](SessionInvite.tag)

  def endpoints(formService: Form.Service[F],
                formSessionService: FormSession.Service[F],
                inviteAnswerService: InviteAnswer.Service[F],
                sessionInviteService: SessionInvite.Service[F],
                userAuth: UserAuth[F]): HttpRoutes[F] = userAuth.authEmployee {
      AuthedRoutes.of[User.Id, F] {
        // Create answer
        case req @ POST -> Root / InviteRoute / SessionInviteIdVar(sessionInviteId) / AnswerRoute as userId =>
          val action = for
            answer <- req.req.attemptAs[InviteAnswer.Create].leftWiden
            result <- inviteAnswerService.create(answer, sessionInviteId, userId).leftWiden
          yield result

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while creating answer.")
          }
        // Update answer
        case req @ PUT -> Root / InviteRoute / SessionInviteIdVar(inviteAnswerId) / AnswerRoute as userId =>
          val action = for
            answer <- req.req.attemptAs[InviteAnswer.Create].leftWiden
            result <- inviteAnswerService.update(answer.withId(inviteAnswerId), userId).leftWiden
          yield result

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while updating answer.")
          }/*
        // Update form
        case req @ PUT -> Root / FormRoute / FormIdVar(id) as companyUser =>
          val action = for
            form <- req.req.attemptAs[Form.Create].leftWiden
            result <- formService.update(form.withId(id), companyUser.id.some).leftWiden
          yield result

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
          val action = for
            formSession <- req.req.attemptAs[FormSession.Create].leftWiden
            result <- formSessionService.create(formSession, formId, companyUser.id).leftWiden
          yield result

          action.value.flatMap {
            case Right(form) => Ok(form)
            case Left(err) => BadRequest(s"Error '$err' while creating form session.")
          }
        // update session
        case req @ PUT -> Root / SessionRoute / FormSessionIdVar(id) as companyUser =>
          val action = for
            form <- req.req.attemptAs[FormSession].leftWiden
            result <- formSessionService.update(form.withId(id), companyUser.id).leftWiden
          yield result

          action.value.flatMap {
            case Right(saved) => Ok(saved)
            case Left(err) => BadRequest(s"Error '$err' while updating form session.")
          }
        // Start session
        case req @ PUT -> Root / SessionRoute / FormSessionIdVar(id) / "start" as companyUser =>
          val action = formSessionService.start(id, companyUser.id)
          action.value.flatMap {
            case Right(saved) => Ok(saved)
            case Left(err) => BadRequest(s"Error '$err' while starting form session.")
          }
        // Finish session
        case req @ PUT -> Root / SessionRoute / FormSessionIdVar(id) / "finish" as companyUser =>
          val action = formSessionService.finish(id, companyUser.id)
          action.value.flatMap {
            case Right(saved) => Ok(saved)
            case Left(err) => BadRequest(s"Error '$err' while finishing form session.")
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
          val action = for
            formSessionInvite <- req.req.attemptAs[SessionInvite[String]].leftWiden
            result <- formSessionInviteService.create(formSessionInvite, formSessionId, companyUser.id).leftWiden
          yield result

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
          val action = for
            invite <- req.req.attemptAs[SessionInvite[String]].leftWiden
            result <- formSessionInviteService.update(invite.withId(inviteId), companyUser.id).leftWiden
          yield result

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
          }*/
    }
  }
}
