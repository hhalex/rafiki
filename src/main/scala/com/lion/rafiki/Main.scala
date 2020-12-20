package com.lion.rafiki

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import tsec.authentication.SecuredRequestHandler

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

object Main extends IOApp {
  def run(args: List[String]) = {
    implicit val blocker = Blocker.liftExecutionContext(ExecutionContext.global)
    val env = System.getenv()
    for {
      client <- BlazeClientBuilder[IO](global).stream
      helloWorldAlg = HelloWorld.impl[IO]
      jokeAlg = Jokes.impl[IO](client)

      httpApp <- Stream.eval(
        Auth.authenticator()
          .map(SecuredRequestHandler(_)
            .liftService(
              // Private routes
              Routes.helloWorldRoutes(helloWorldAlg)
            )
            // Public routes
            <+> Routes.uiRoutes
            <+> Routes.jokeRoutes(jokeAlg)
          )
          .map(_.orNotFound)
          .map(Logger.httpApp[IO](true, true)(_))
      )

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract a segments not checked
      // in the underlying routes.

      // With Middlewares in place

      exitCode <- BlazeServerBuilder[IO](global)
        .bindHttp(env.getOrDefault("PORT", "8080").toInt, "0.0.0.0")
        .withHttpApp(httpApp)
        .serve
    } yield exitCode
  }.drain.compile.drain.as(ExitCode.Success)
}