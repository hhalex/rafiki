package com.lion.rafiki.api

import cats.effect.IO
import com.lion.rafiki.sql.{companies, users}
import tsec.passwordhashers.jca.BCrypt
import doobie.util.transactor.Transactor
import doobie.implicits._
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.jsonOf
import tsec.authentication.{TSecAuthService, TSecBearerToken, asAuthed, _}
import org.http4s.dsl.Http4sDsl
import org.http4s.EntityDecoder

object Admin {
  val dsl = new Http4sDsl[IO]{}
  import dsl._
  case class CompanyADD(name: String, rh_user_email: String, rh_user_password: String)
  def createCompanyAndUser(add: CompanyADD)(implicit xa: Transactor.Aux[IO, Unit]) = for {
    hash <- BCrypt.hashpw[IO](add.rh_user_password)
    user <- users.insert(add.rh_user_email, hash).transact(xa)
    company <- companies.insert(add.name, user.id).transact(xa)
  } yield (user, company)

  case class CompanyEDIT(id: companies.Id, name: Option[String], rh_user_email: Option[String], rh_user_password: Option[String])
  def editCompanyAndUser(edit: CompanyEDIT)(implicit xa: Transactor.Aux[IO, Unit]) = for {
    company <- companies.update(edit.id, edit.name).transact(xa)
    maybePassword <- edit.rh_user_password
      .map(p => BCrypt.hashpw[IO](p).map(Some(_)))
      .getOrElse(IO.pure(None))
    user <- users.update(company.rh_user, edit.rh_user_email, maybePassword).transact(xa)
  } yield (user, company)

  def removeCompanyAndUser(id: companies.Id)(implicit xa: Transactor.Aux[IO, Unit]) = for {
    company <- companies.delete(id).transact(xa)
    user <- users.delete(company.rh_user).transact(xa)
  } yield (user, company)

  def listAllCompaniesAndUser()(implicit xa: Transactor.Aux[IO, Unit]) =
    companies.listAllWithUsers().transact(xa)

  def createCompanyCRUD(implicit xa: Transactor.Aux[IO, Unit]): TSecAuthService[users.T, TSecBearerToken[users.Id], IO] = {
    implicit val companyIdDecoder: Decoder[companies.Id] = Decoder[Int].map(companies.tagSerial)

    implicit val companyAddDecoder: Decoder[CompanyADD] = deriveDecoder
    implicit val entityCompanyAddDecoder: EntityDecoder[IO, CompanyADD] = jsonOf[IO, CompanyADD]

    implicit val companyEditDecoder: Decoder[CompanyEDIT] = deriveDecoder
    implicit val entityCompanyEditDecoder: EntityDecoder[IO, CompanyEDIT] = jsonOf[IO, CompanyEDIT]

    implicit val entityCompanyIdDecoder: EntityDecoder[IO, companies.Id] = jsonOf[IO, companies.Id]

    implicit val companyIdEncoder: Encoder[companies.Id] = Encoder[Int].contramap(_.asInstanceOf[Int])
    implicit val userIdEncoder: Encoder[users.Id] = Encoder[Int].contramap(_.asInstanceOf[Int])
    implicit val userEncoder: Encoder[users.T] = Encoder.instance { u => Json.obj("id" -> u.id.asJson, "username" -> u.username.asJson)}
    implicit val companyEncoder: Encoder[companies.T] = deriveEncoder

    TSecAuthService[users.T, TSecBearerToken[users.Id], IO] {
      case securedRequest @ POST -> Root / "company" asAuthed _ => {
        for {
          add <- securedRequest.request.as[CompanyADD]
          addedCompanyAndUser <- createCompanyAndUser(add)
          response <- Ok(addedCompanyAndUser.asJson)
        } yield response
      }
      case securedRequest @ PUT -> Root / "company" asAuthed _ => {
        for {
          edit <- securedRequest.request.as[CompanyEDIT]
          editedCompany <- editCompanyAndUser(edit)
          response <- Ok(editedCompany.asJson)
        } yield response
      }
      case securedRequest @ DELETE -> Root / "company" asAuthed _ => {
        for {
          id <- securedRequest.request.as[companies.Id]
          removedCompanyAndUser <- removeCompanyAndUser(id)
          response <- Ok(removedCompanyAndUser.asJson)
        } yield response
      }
      case GET -> Root / "company" asAuthed _ => {
        for {
          allCompaniesAndUser <- listAllCompaniesAndUser()
          response <- Ok(allCompaniesAndUser.asJson)
        } yield response
      }
    }
  }

}
