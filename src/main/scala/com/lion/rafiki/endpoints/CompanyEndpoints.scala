package com.lion.rafiki.endpoints

import cats.effect.Sync
import cats.syntax.all._
import com.lion.rafiki.auth.Role
import com.lion.rafiki.domain.{Company, User}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import tsec.authentication._
import tsec.authorization.AuthorizationInfo

class CompanyEndpoints[F[_]: Sync] extends Http4sDsl[F] {
  import Pagination._, CirceEntityDecoder._, CirceEntityEncoder._

  object CompanyIdVar extends IdVar[Company.Id](Company.tagSerial)

  private def createCompanyEndpoint(companyService: Company.Service[F]): AuthEndpoint[F] = {
    case req @ POST -> Root asAuthed _ =>
      val action = for {
        companyWithUser <- req.request.as[Company.Create]
        result <- companyService.create(companyWithUser).value
      } yield result

      action.flatMap({
        case Right(createdCompanyAndUser) => Ok(createdCompanyAndUser)
        case Left(err) => Conflict(s"Error '$err' while creating company.")
      })

  }

  private def updateCompanyEndpoint(companyService: Company.Service[F]): AuthEndpoint[F] = {
    case req @ PUT -> Root / CompanyIdVar(id) asAuthed _ =>
      val action = for {
        company <- req.request.as[Company.Create]
        result <- companyService.update(company.withId(id)).value
      } yield result

      action.flatMap {
        case Right(saved) => Ok(saved)
        case Left(_) => NotFound("The company was not found")
      }
  }

  private def getCompanyEndpoint(companyService: Company.Service[F]): AuthEndpoint[F] = {
    case GET -> Root / CompanyIdVar(id) asAuthed _ =>
      companyService.get(id).value.flatMap {
        case Right(found) => Ok(found)
        case Left(_) => NotFound("The company was not found")
      }
  }

  private def deleteCompanyEndpoint(companyService: Company.Service[F]): AuthEndpoint[F] = {
    case DELETE -> Root / CompanyIdVar(id) asAuthed _ =>
      companyService.get(id).value.flatMap({
        case Right(company) => for {
            _ <- companyService.delete(id)
            resp <- Ok()
        } yield resp
        case Left(_) => NotFound("The company was not found")
      })
  }

  private def listCompaniesEndpoint(companyService: Company.Service[F]): AuthEndpoint[F] = {
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(
    offset,
    ) asAuthed _ =>
      for {
        retrieved <- companyService.listWithUser(pageSize.getOrElse(10), offset.getOrElse(0))
        resp <- Ok(retrieved)
      } yield resp
  }

  def endpoints(
                 companyService: Company.Service[F],
                 auth: SecuredRequestHandler[F, User.Id, User.Authed, Auth],
               )(implicit A: AuthorizationInfo[F, Role, User.Authed]): HttpRoutes[F] = {

    val authEndpoints: AuthService[F] = Authentication.adminOnly(
        createCompanyEndpoint(companyService)
          .orElse(getCompanyEndpoint(companyService))
          .orElse(deleteCompanyEndpoint(companyService))
          .orElse(updateCompanyEndpoint(companyService))
          .orElse(listCompaniesEndpoint(companyService))
      )

    auth.liftService(authEndpoints)
  }
}
