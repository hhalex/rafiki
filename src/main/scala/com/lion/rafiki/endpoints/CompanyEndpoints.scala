package com.lion.rafiki.endpoints

import cats.effect.Sync
import cats.syntax.all._
import com.lion.rafiki.auth.UserAuth
import com.lion.rafiki.domain.{Company, CompanyContract, User, ValidationError}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, HttpRoutes}

class CompanyEndpoints[F[_]: Sync] extends Http4sDsl[F] {
  import Pagination._, CirceEntityDecoder._, CirceEntityEncoder._

  val CompanyRoute = "company"
  val CompanyContractRoute = "contract"

  object CompanyIdVar extends IdVar[Company.Id](Company.tagSerial)
  object CompanyContractIdVar extends IdVar[CompanyContract.Id](CompanyContract.tagSerial)

  def endpoints(companyService: Company.Service[F], companyContractService: CompanyContract.Service[F], userAuth: UserAuth[F]): HttpRoutes[F] =
    userAuth.authAdmin {
      AuthedRoutes.of[User.Authed, F] {
        // Create company
        case req @ POST -> Root / CompanyRoute as _ =>
          val action = for {
            companyWithUser <- req.req.attemptAs[Company.Create].leftMap(ValidationError.Decoding)
            result <- companyService.create(companyWithUser)
          } yield result

          action.value.flatMap {
            case Right(createdCompanyAndUser) => Ok(createdCompanyAndUser)
            case Left(err) => BadRequest(s"Error '$err' while creating company.")
          }
        // Update company
        case req @ PUT -> Root / CompanyRoute / CompanyIdVar(id) as _ =>
          val action = for {
            company <- req.req.attemptAs[Company.Create].leftMap(ValidationError.Decoding)
            result <- companyService.update(company.withId(id))
          } yield result

          action.value.flatMap {
            case Right(saved) => Ok(saved)
            case Left(err) => BadRequest(s"Error '$err' while updating the company")
          }
        // Get company
        case GET -> Root / CompanyRoute / CompanyIdVar(id) as _ =>
          companyService.get(id).value.flatMap {
            case Right(found) => Ok(found)
            case Left(err) => BadRequest(s"Error '$err' while getting the company")
          }
        // Delete company
        case DELETE -> Root / CompanyRoute / CompanyIdVar(id) as _ =>
          companyService.delete(id).value.flatMap {
            case Right(company) => Ok()
            case Left(err) => BadRequest(s"Error '$err' while deleting the company")
          }
        // List companies
        case GET -> Root / CompanyRoute :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) as _ =>
          // TODO: check optional parameters to use 'getWithUser' (without pagination)
          companyService.listWithUser(pageSize.getOrElse(10), offset.getOrElse(0)).value.flatMap {
            case Right(companies) => Ok(companies)
            case Left(err) => BadRequest(s"Error '$err' while deleting the company")
          }
          // TODO: Change REST api for company contracts
        // Create company contract
        case req @ POST -> Root / CompanyRoute / CompanyIdVar(companyId) / CompanyContractRoute as _ =>
          val action = for {
            contract <- req.req.attemptAs[CompanyContract.CreateRecord].leftMap(ValidationError.Decoding)
            result <- companyContractService.create(contract.copy(company = companyId))
          } yield result

          action.value.flatMap {
            case Right(createdCompanyContract) => Ok(createdCompanyContract)
            case Left(err) => BadRequest(s"Error '$err' while creating company contract.")
          }
        // Update company contract
        case req @ PUT -> Root / CompanyRoute / CompanyIdVar(companyId) / CompanyContractRoute / CompanyContractIdVar(contractId) as _ =>
          val action = for {
            companyContract <- req.req.attemptAs[CompanyContract.CreateRecord].leftMap(ValidationError.Decoding)
            result <- companyContractService.update(companyContract.copy(company = companyId).withId(contractId))
          } yield result

          action.value.flatMap {
            case Right(saved) => Ok(saved)
            case Left(err) => BadRequest(s"Error '$err' while updating company contract.")
          }
        // Get company contract
        case GET -> Root / CompanyRoute / CompanyIdVar(companyId) / CompanyContractRoute / CompanyContractIdVar(id) as _ =>
          companyContractService.get(id).value.flatMap {
            case Right(found) if found.data.company == companyId => Ok(found)
            case Right(_) => BadRequest(s"Error while getting company contract (malformed request).")
            case Left(err) => BadRequest(s"Error '$err' while getting company contract.")
          }
        // Delete company contract
        case req@DELETE -> Root / CompanyRoute / CompanyIdVar(companyId) / CompanyContractRoute / CompanyContractIdVar(id) as _ =>
          companyContractService.get(id).value.flatMap {
            case Right(companyContract) if companyContract.data.company == companyId =>
              companyContractService.delete(id).value.flatMap {
                case Right(_) => Ok()
                case Left(err) => BadRequest(s"Error '$err' while deleting company contract.")
              }
            case Right(_) => BadRequest(s"Error while deleting company contract (malformed request).")
            case Left(err) => BadRequest(s"Error '$err' while deleting company contract.")
          }
        // List company contracts of a given company
        case GET -> Root / CompanyRoute / CompanyIdVar(companyId) / CompanyContractRoute :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(
        offset,
        ) as _ =>
          // TODO: check optional parameters to use 'getWithUser' (without pagination)
          companyContractService.listByCompany(companyId, pageSize.getOrElse(10), offset.getOrElse(0)).value.flatMap {
            case Right(list) => Ok(list)
            case Left(err) => BadRequest(s"Error '$err' while listing company contracts.")
          }
        // List all company contracts
        case GET -> Root / CompanyRoute / "contract" :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(
        offset,
        ) as _ =>
          // TODO: check optional parameters to use 'getWithUser' (without pagination)
          companyContractService.list(pageSize.getOrElse(10), offset.getOrElse(0)).value.flatMap {
            case Right(list) => Ok(list)
            case Left(err) => BadRequest(s"Error '$err' while listing all company contracts.")
          }
      }
    }
}
