package com.lion.rafiki

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import com.lion.rafiki.auth.{TokenStore, UserStore}
import com.lion.rafiki.domain.{Company, CompanyContract, User}
import com.lion.rafiki.endpoints.{Authentication, CompanyEndpoints, UserEndpoints}
import com.lion.rafiki.sql.{DoobieCompanyContractRepo, DoobieCompanyRepo, DoobieUserRepo, create}
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
      userService = new User.Service[IO](userRepo, new User.FromRepoValidation[IO](userRepo))

      companyRepo = new DoobieCompanyRepo[IO](xa)
      companyValidation = new Company.FromRepoValidation[IO](companyRepo)
      companyService = new Company.Service[IO](companyRepo, companyValidation, userService)
      companyContractRepo = new DoobieCompanyContractRepo[IO](xa)
      companyContractService = new CompanyContract.Service[IO](companyContractRepo, companyValidation)

      initialUserStore = UserStore(userService, conf.hotUsersList)
      tokenStore <- Stream.eval(TokenStore.empty)

      auth = BearerTokenAuthenticator(
        tokenStore,
        initialUserStore.identityStore,
        TSecTokenSettings(
          expiryDuration = 10.minutes,
          maxIdle = None
        ))
      routeAuth = SecuredRequestHandler(auth)
      authorizationInfo = Authentication.authRole[IO](userService, companyService)

      companyEndpoints = new CompanyEndpoints[IO]().endpoints(companyService, companyContractService, routeAuth)(authorizationInfo)
      userEndpoints = new UserEndpoints[IO]().endpoints(userService, initialUserStore, routeAuth)(authorizationInfo)

      exitCode <- BlazeServerBuilder[IO](global)
        .bindHttp(conf.port, conf.host)
        .withHttpApp(Logger.httpApp[IO](true, true)(
          Router(
            "/" -> (userEndpoints <+> Routes.uiRoutes),
            "/api" -> companyEndpoints
          ).orNotFound
        ))
        .serve
    } yield exitCode
  }.drain.compile.drain.as(ExitCode.Success)
}