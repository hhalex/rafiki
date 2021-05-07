package com.lion.rafiki

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.semigroupk._
import com.lion.rafiki.auth.{
  CryptoBits,
  HotUserStore,
  PasswordHasher,
  PrivateKey,
  UserAuth
}
import com.lion.rafiki.domain.company.{Form, FormSession, FormSessionInvite}
import com.lion.rafiki.domain.{Company, CompanyContract, User}
import com.lion.rafiki.endpoints.{
  AuthenticationEndpoints,
  CompanyBusinessEndpoints,
  CompanyEndpoints,
  UserEndpoints
}
import com.lion.rafiki.sql.{
  DoobieCompanyContractRepo,
  DoobieCompanyRepo,
  DoobieFormRepo,
  DoobieFormSessionInviteRepo,
  DoobieInviteAnswerRepo,
  DoobieFormSessionRepo,
  DoobieUserRepo,
  create
}
import doobie.util.transactor.Transactor
import doobie.implicits._
import fs2._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger

import java.time.Clock
import com.github.nscala_time.time.Imports.DateTime
import scala.concurrent.ExecutionContext.global

object Main extends IOApp {
  def run(args: List[String]) = {

    for
      conf <- Stream.eval(Conf[IO]())
      xa = Transactor.fromDriverManager[IO](
        "org.postgresql.Driver",
        conf.dbUrl,
        conf.dbUser,
        conf.dbPassword
      )
      _ <- Stream.eval(create.allTables.transact(xa))

      exitCode <- {

        val passwordHasher = PasswordHasher.bcrypt[IO]()

        val userRepo = new DoobieUserRepo[IO](xa)
        val userService = new User.Service[IO](userRepo, passwordHasher)
        val companyRepo = new DoobieCompanyRepo[IO](xa)

        val companyService = new Company.Service[IO](companyRepo, userService)
        val companyContractRepo = new DoobieCompanyContractRepo[IO](xa)
        val companyContractValidation =
          new CompanyContract.FromRepoValidation[IO](companyContractRepo)
        val companyContractService =
          new CompanyContract.Service[IO](companyContractRepo)

        val inviteAnswerRepo = new DoobieInviteAnswerRepo[IO](xa)

        val formRepo = new DoobieFormRepo[IO](xa)
        val formValidation = new Form.FromRepoValidation[IO](formRepo)
        val formService = new Form.Service[IO](formRepo, inviteAnswerRepo, formValidation)

        val formSessionRepo = new DoobieFormSessionRepo[IO](xa)
        val formSessionValidation = new FormSession.FromRepoValidation[IO](
          formSessionRepo,
          formValidation,
          companyContractRepo
        )

        val formSessionService =
          new FormSession.Service[IO](formSessionRepo, formSessionValidation, DateTime.now)
        val formSessionInviteRepo = new DoobieFormSessionInviteRepo[IO](xa)
        val formSessionInviteValidation =
          new FormSessionInvite.FromRepoValidation[IO](
            formSessionInviteRepo,
            formSessionValidation
          )
        val formSessionInviteService = new FormSessionInvite.Service[IO](
          formSessionInviteRepo,
          formSessionInviteValidation,
          formSessionValidation,
          userService
        )

        val privateKey = PrivateKey(
          scala.io.Codec.toUTF8(
            scala.util.Random.alphanumeric.take(20).mkString("")
          )
        )
        val crypto = CryptoBits(privateKey)
        val clock = Clock.systemUTC()

        val hotUserStore =
          new HotUserStore[IO](conf.hotUsersList, passwordHasher)
        val userAuth =
          new UserAuth[IO](userService, companyRepo, hotUserStore, crypto)

        val authEndpoints =
          new AuthenticationEndpoints[IO]().endpoints(userAuth, clock)
        val companyEndpoints = new CompanyEndpoints[IO]().endpoints(
          companyService,
          companyContractService,
          userAuth
        )
        val userEndpoints =
          new UserEndpoints[IO]().endpoints(userService, userAuth)
        val companyBusinessEndpoints =
          new CompanyBusinessEndpoints[IO]().endpoints(
            formService,
            formSessionService,
            formSessionInviteService,
            userAuth
          )

        val httpApp = Router(
          "/" -> (authEndpoints <+> Routes.uiRoutes),
          "/api" -> Router(
            "/company" -> companyBusinessEndpoints,
            "/admin" -> (companyEndpoints <+> userEndpoints)
          )
        ).orNotFound

        BlazeServerBuilder[IO](global)
          .bindHttp(conf.port, conf.host)
          .withHttpApp(Logger.httpApp[IO](true, true)(httpApp))
          .serve
      }
    yield exitCode
  }.drain.compile.drain.as(ExitCode.Success)
}
