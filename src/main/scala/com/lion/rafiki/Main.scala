package com.lion.rafiki

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import com.lion.rafiki.auth.UserStore.UserId
import com.lion.rafiki.auth.{TokenStore, UserStore, UsernamePasswordCredentials}
import com.lion.rafiki.sql.{create, users}
import doobie.util.transactor.Transactor
import doobie.implicits._
import fs2.Stream
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import tsec.authentication.{BackingStore, BearerTokenAuthenticator, SecuredRequestHandler, TSecBearerToken, TSecTokenSettings}
import tsec.common.SecureRandomId

import java.net.URI
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt

object Main extends IOApp {
  def createHttpApp(userStore: UserStore, tokenStore: BackingStore[IO, SecureRandomId, TSecBearerToken[UserId]], client: Client[IO])(implicit b: Blocker) = {
    val auth = BearerTokenAuthenticator(
      tokenStore,
      userStore.identityStore,
      TSecTokenSettings(
        expiryDuration = 10.minutes,
        maxIdle = None
      ))

    val helloWorldAlg = HelloWorld.impl[IO]
    val jokeAlg = Jokes.impl[IO](client)
    val loginRoutes = Routes.loginRoute(auth, userStore.checkPassword)
    val securedRoutes = SecuredRequestHandler(auth).liftService(
      // Private routes
      Routes.helloWorldRoutes(helloWorldAlg)
    )

    (Routes.uiRoutes <+> Routes.jokeRoutes(jokeAlg) <+> loginRoutes <+> securedRoutes).orNotFound
  }

  def run(args: List[String]) = {
    implicit val blocker = Blocker.liftExecutionContext(ExecutionContext.global)
    val env = System.getenv()

    for {
      c <- Stream.eval(Conf[IO](env))
      xa = Transactor.fromDriverManager[IO](
        "org.postgresql.Driver",
        c.dbUrl,
        c.dbUser,
        c.dbPassword,
        blocker
      )
      client <- BlazeClientBuilder[IO](global).stream
      _ <- Stream.eval(create.users.run.transact(xa))
      initialUserStore = UserStore(xa, UsernamePasswordCredentials("username", "pass"))
      tokenStore <- Stream.eval(TokenStore.empty)
      exitCode <- BlazeServerBuilder[IO](global)
        .bindHttp(c.port, c.host)
        .withHttpApp(Logger.httpApp[IO](true, true)(
          createHttpApp(initialUserStore, tokenStore, client)
        ))
        .serve
    } yield exitCode
  }.drain.compile.drain.as(ExitCode.Success)
}