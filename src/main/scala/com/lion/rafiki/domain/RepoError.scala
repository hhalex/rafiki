package com.lion.rafiki.domain

import cats.data.EitherT
import doobie.ConnectionIO
import doobie.implicits.toDoobieApplicativeErrorOps
import java.sql.SQLException

sealed trait RepoError

object RepoError {

  case object NotFound extends RepoError
  case class Other(s: String) extends RepoError

  def fromSQLException(e: SQLException): RepoError = {
    e match {
      case exception =>
        Other(exception.getMessage)
    }
  }

  implicit class ConnectionIOwithErrors[T](c: ConnectionIO[T]) {
    def toResult(): EitherT[ConnectionIO, RepoError, T] = EitherT(c.attemptSql).leftMap(RepoError.fromSQLException)
  }
}




