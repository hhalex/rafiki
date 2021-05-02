package com.lion.rafiki.domain

import com.lion.rafiki.auth.{AuthError, PasswordError}
import org.http4s.DecodeFailure

sealed trait ValidationError extends Product with Serializable

object ValidationError {
  case object UserNotFound extends ValidationError
  case object UserCredentialsIncorrect extends ValidationError
  case object CompanyNotFound extends ValidationError
  case object CompanyContractNotFound extends ValidationError
  case class UserAlreadyExists(data: User.Record) extends ValidationError
  case class CompanyAlreadyExists(data: Company.Record) extends ValidationError

  case object FormNotFound extends ValidationError
  case object NotAllowed extends ValidationError

  case object CompanyContractFull extends ValidationError

  case class Repo(e: RepoError) extends ValidationError
  case class Decoding(e: DecodeFailure) extends ValidationError
  case class Auth(e: AuthError) extends ValidationError
  case class Password(e: PasswordError) extends ValidationError
}
