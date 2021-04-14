package com.lion.rafiki

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import com.lion.rafiki.auth.{TokenStore, UserStore}
import com.lion.rafiki.domain.{Company, CompanyContract, Form, User}
import com.lion.rafiki.endpoints.{Authentication, CompanyBusinessEndpoints, CompanyEndpoints, UserEndpoints}
import com.lion.rafiki.sql.{DoobieCompanyContractRepo, DoobieCompanyRepo, DoobieFormRepo, DoobieUserRepo, create}
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

      formRepo = new DoobieFormRepo[IO](xa)
      formService = new Form.Service[IO](formRepo)

      initialUserStore = UserStore(userService, companyService, conf.hotUsersList)
      tokenStore <- Stream.eval(TokenStore.empty)

      auth = BearerTokenAuthenticator(
        tokenStore,
        initialUserStore.identityStore,
        TSecTokenSettings(
          expiryDuration = 10.minutes,
          maxIdle = None
        ))
      routeAuth = SecuredRequestHandler(auth)
      authorizationInfo = Authentication.authRole[IO]()

      companyEndpoints = new CompanyEndpoints[IO]().endpoints(companyService, companyContractService, routeAuth)(authorizationInfo)
      userEndpoints = new UserEndpoints[IO]().endpoints(userService, initialUserStore, routeAuth)(authorizationInfo)
      companyBusinessEndpoints = new CompanyBusinessEndpoints[IO]().endpoints(companyService, formService, routeAuth)(authorizationInfo)

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