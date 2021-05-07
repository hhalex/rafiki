package com.lion.rafiki.domain

import com.lion.rafiki.auth.{AuthError, PasswordError}
import org.http4s.DecodeFailure
import com.lion.rafiki.domain.company.FormSession

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

  // form sessions
  case object FormSessionBrokenState extends ValidationError
  case class FormSessionCantStart(state: FormSession.State) extends ValidationError
  case class FormSessionCantFinish(state: FormSession.State) extends ValidationError

  case class Repo(e: RepoError) extends ValidationError
  case class Decoding(e: DecodeFailure) extends ValidationError
  case class Auth(e: AuthError) extends ValidationError
  case class Password(e: PasswordError) extends ValidationError
}
