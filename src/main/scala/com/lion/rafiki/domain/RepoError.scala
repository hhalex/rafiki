package com.lion.rafiki.domain

import org.postgresql.util.PSQLException

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
}

