package com.lion.rafiki.endpoints

import cats.effect.Sync
import cats.syntax.all._
import com.lion.rafiki.auth.Role
import com.lion.rafiki.domain.{Company, CompanyContract, User}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import org.http4s.server.Router
import tsec.authentication._
import tsec.authorization.AuthorizationInfo

class CompanyEndpoints[F[_]: Sync] extends Http4sDsl[F] {
  import Pagination._, CirceEntityDecoder._, CirceEntityEncoder._

  object CompanyIdVar extends IdVar[Company.Id](Company.tagSerial)
  object CompanyContractIdVar extends IdVar[CompanyContract.Id](CompanyContract.tagSerial)

  // Global Company endpoints

  private def createCompanyEndpoint(companyService: Company.Service[F]): AuthEndpoint[F] = {
    case req @ POST -> Root asAuthed _ =>
      val action = for {
        companyWithUser <- req.request.as[Company.Create]
        result <- companyService.create(companyWithUser).value
      } yield result

      action.flatMap({
        case Right(createdCompanyAndUser) => Ok(createdCompanyAndUser)
        case Left(err) => BadRequest(s"Error '$err' while creating company.")
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
        case Left(err) => BadRequest(s"Error '$err' while updating the company")
      }
  }

  private def getCompanyEndpoint(companyService: Company.Service[F]): AuthEndpoint[F] = {
    case GET -> Root / CompanyIdVar(id) asAuthed _ =>
      companyService.get(id).value.flatMap {
        case Right(found) => Ok(found)
        case Left(err) => BadRequest(s"Error '$err' while getting the company")
      }
  }

  private def deleteCompanyEndpoint(companyService: Company.Service[F]): AuthEndpoint[F] = {
    case DELETE -> Root / CompanyIdVar(id) asAuthed _ =>
      companyService.delete(id).value.flatMap {
        case Right(company) => Ok()
        case Left(err) => BadRequest(s"Error '$err' while deleting the company")
      }
  }

  private def listCompaniesEndpoint(companyService: Company.Service[F]): AuthEndpoint[F] = {
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed _ =>
      companyService.listWithUser(pageSize.getOrElse(10), offset.getOrElse(0)).value.flatMap {
        case Right(companies) => Ok(companies)
        case Left(err) => BadRequest(s"Error '$err' while deleting the company")
      }
  }

  // Global company contracts endpoints

  private def createCompanyContractEndpoint(companyContractService: CompanyContract.Service[F]): AuthEndpoint[F] = {
    case req @ POST -> Root / CompanyIdVar(companyId) / "contract" asAuthed _ =>
      val action = for {
        contract <- req.request.as[CompanyContract.CreateRecord]
        result <- companyContractService.create(contract.copy(company = companyId)).value
      } yield result

      action.flatMap({
        case Right(createdCompanyContract) => Ok(createdCompanyContract)
        case Left(err) => BadRequest(s"Error '$err' while creating company contract '${req.request.as[String]}'.")
      })
  }

  private def updateCompanyContractEndpoint(companyContractService: CompanyContract.Service[F]): AuthEndpoint[F] = {
    case req @ PUT -> Root / CompanyIdVar(companyId) / "contract" / CompanyContractIdVar(contractId) asAuthed _ =>
      val action = for {
        companyContract <- req.request.as[CompanyContract.CreateRecord]
        result <- companyContractService.update(companyContract.copy(company = companyId).withId(contractId)).value
      } yield result

      action.flatMap {
        case Right(saved) => Ok(saved)
        case Left(err) => BadRequest(s"Error '$err' while updating company contract '${req.request.as[String]}'.")
      }
  }

  private def getCompanyContractEndpoint(companyContractService: CompanyContract.Service[F]): AuthEndpoint[F] = {
    case req @ GET -> Root / CompanyIdVar(companyId) / "contract" / CompanyContractIdVar(id) asAuthed _ =>
      companyContractService.get(id).value.flatMap {
        case Right(found) if found.data.company == companyId => Ok(found)
        case Right(_) => BadRequest(s"Error while getting company contract (malformed request).")
        case Left(err) => BadRequest(s"Error '$err' while getting company contract '${req.request.as[String]}'.")
      }
  }

  private def deleteCompanyContractEndpoint(companyContractService: CompanyContract.Service[F]): AuthEndpoint[F] = {
    case req @ DELETE -> Root / CompanyIdVar(companyId) / "contract" / CompanyContractIdVar(id) asAuthed _ =>
      companyContractService.get(id).value.flatMap({
        case Right(companyContract) if companyContract.data.company == companyId => for {
          _ <- companyContractService.delete(id)
          resp <- Ok()
        } yield resp
        case Right(_) => BadRequest(s"Error while deleting company contract (malformed request).")
        case Left(err) => BadRequest(s"Error '$err' while deleting company contract '${req.request.as[String]}'.")
      })
  }

  private def listCompanyContractsEndpoint(companyContractService: CompanyContract.Service[F]): AuthEndpoint[F] = {
    case GET -> Root / CompanyIdVar(companyId) / "contract" :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(
    offset,
    ) asAuthed _ =>
      for {
        retrieved <- companyContractService.listByCompany(companyId, pageSize.getOrElse(10), offset.getOrElse(0))
        resp <- Ok(retrieved)
      } yield resp
  }

  private def listAllCompanyContractsEndpoint(companyContractService: CompanyContract.Service[F]): AuthEndpoint[F] = {
    case GET -> Root / "contract":? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(
    offset,
    ) asAuthed _ =>
      for {
        retrieved <- companyContractService.list(pageSize.getOrElse(10), offset.getOrElse(0))
        resp <- Ok(retrieved)
      } yield resp
  }

  def endpoints(
                 companyService: Company.Service[F],
                 companyContractService: CompanyContract.Service[F],
                 auth: SecuredRequestHandler[F, User.Id, User.Authed, Auth],
               )(implicit A: AuthorizationInfo[F, Role, User.Authed]): HttpRoutes[F] = {

    val authEndpoints: AuthService[F] = Authentication.adminOnly(
        createCompanyEndpoint(companyService)
          .orElse(getCompanyEndpoint(companyService))
          .orElse(deleteCompanyEndpoint(companyService))
          .orElse(updateCompanyEndpoint(companyService))
          .orElse(listCompaniesEndpoint(companyService))
      )

    val authCompanyContractEndpoints: AuthService[F] = Authentication.adminOnly(
      createCompanyContractEndpoint(companyContractService)
        .orElse(getCompanyContractEndpoint(companyContractService))
        .orElse(deleteCompanyContractEndpoint(companyContractService))
        .orElse(updateCompanyContractEndpoint(companyContractService))
        .orElse(listCompanyContractsEndpoint(companyContractService))
        .orElse(listAllCompanyContractsEndpoint(companyContractService))
    )

    Router(
      "/company" -> auth.liftService(authEndpoints <+> authCompanyContractEndpoints)
    )
  }
}
