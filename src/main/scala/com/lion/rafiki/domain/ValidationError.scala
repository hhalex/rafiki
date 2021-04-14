package com.lion.rafiki.domain

sealed trait ValidationError extends Product with Serializable

object ValidationError {
  case object UserNotFound extends ValidationError
  case object CompanyNotFound extends ValidationError
  case object CompanyContractNotFound extends ValidationError
  case class UserAlreadyExists(data: User.Record) extends ValidationError
  case class CompanyAlreadyExists(data: Company.Record) extends ValidationError

  case object FormNotFound extends ValidationError
  case object NotAllowed extends ValidationError

  case class Repo(e: RepoError) extends ValidationError
}
