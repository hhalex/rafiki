package com.lion.rafiki

import cats.effect.{Blocker, ExitCode, IO, IOApp}

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  def run(args: List[String]) = {
    implicit val blocker = Blocker.liftExecutionContext(ExecutionContext.global)
    Server.stream[IO].compile.drain.as(ExitCode.Success)
  }
}