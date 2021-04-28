package com.lion.rafiki

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import com.lion.rafiki.auth.{TokenStore, UserStore}
import com.lion.rafiki.domain.company.{Form, FormSession, FormSessionInvite}
import com.lion.rafiki.domain.{Company, CompanyContract, User}
import com.lion.rafiki.endpoints.{Authentication, CompanyBusinessEndpoints, CompanyEndpoints, UserEndpoints}
import com.lion.rafiki.sql.{DoobieCompanyContractRepo, DoobieCompanyRepo, DoobieFormRepo, DoobieFormSessionInviteRepo, DoobieFormSessionRepo, DoobieUserRepo, create}
import doobie.util.transactor.Transactor
import doobie.implicits._
import org.http4s.implicits._
import fs2.Stream
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import tsec.authentication.{BearerTokenAuthenticator, SecuredRequestHandler, TSecTokenSettings}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt

object Main extends IOApp {
  def run(args: List[String]) = {
    implicit val blocker = Blocker.liftExecutionContext(ExecutionContext.global)

    for {
      conf <- Stream.eval(Conf[IO]())
      xa = Transactor.fromDriverManager[IO](
        "org.postgresql.Driver",
        conf.dbUrl,
        conf.dbUser,
        conf.dbPassword,
        blocker
      )
      _ <- Stream.eval(create.allTables.transact(xa))

      userRepo = new DoobieUserRepo[IO](xa)
      userService = new User.Service[IO](userRepo)

      companyRepo = new DoobieCompanyRepo[IO](xa)
      companyService = new Company.Service[IO](companyRepo, userService)
      companyContractRepo = new DoobieCompanyContractRepo[IO](xa)
      companyContractValidation = new CompanyContract.FromRepoValidation[IO](companyContractRepo)
      companyContractService = new CompanyContract.Service[IO](companyContractRepo)

      formRepo = new DoobieFormRepo[IO](xa)
      formValidation = new Form.FromRepoValidation[IO](formRepo)
      formService = new Form.Service[IO](formRepo, formValidation)

      formSessionRepo = new DoobieFormSessionRepo[IO](xa)
      formSessionValidation = new FormSession.FromRepoValidation[IO](formSessionRepo, formValidation, companyContractRepo)
      formSessionService = new FormSession.Service[IO](formSessionRepo, formSessionValidation)

      formSessionInviteRepo = new DoobieFormSessionInviteRepo[IO](xa)
      formSessionInviteValidation = new FormSessionInvite.FromRepoValidation[IO](formSessionInviteRepo, formSessionValidation)
      formSessionInviteService = new FormSessionInvite.Service[IO](formSessionInviteRepo, formSessionInviteValidation, userService)

      initialUserStore = UserStore(userService, companyService, conf.hotUsersList)
      tokenStore <- Stream.eval(TokenStore.empty)

      auth = BearerTokenAuthenticator(
        tokenStore,
        initialUserStore.identityStore,
        TSecTokenSettings(
          expiryDuration = 30.minutes,
          maxIdle = None
        ))
      routeAuth = SecuredRequestHandler(auth)
      authorizationInfo = Authentication.authRole[IO]()

      companyEndpoints = new CompanyEndpoints[IO]().endpoints(companyService, companyContractService, routeAuth)(authorizationInfo)
      userEndpoints = new UserEndpoints[IO]().endpoints(userService, initialUserStore, routeAuth)(authorizationInfo)
      companyBusinessEndpoints = new CompanyBusinessEndpoints[IO]().endpoints(companyService, formService, formSessionService, formSessionInviteService, routeAuth)(authorizationInfo)

      exitCode <- BlazeServerBuilder[IO](global)
        .bindHttp(conf.port, conf.host)
        .withHttpApp(Logger.httpApp[IO](true, true)(
          Router(
            "/" -> (userEndpoints <+> Routes.uiRoutes),
            "/api" -> Router(
              "/" -> companyEndpoints,
              "/company" -> companyBusinessEndpoints
            )
          ).orNotFound
        ))
        .serve
    } yield exitCode
  }.drain.compile.drain.as(ExitCode.Success)
}