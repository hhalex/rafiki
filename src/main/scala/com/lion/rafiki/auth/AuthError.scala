package com.lion.rafiki.auth

import com.lion.rafiki.domain.RepoError

sealed trait AuthError

object AuthError {
  case class Password(err: PasswordError) extends AuthError

  case object UserNotFound extends AuthError
  case object InvalidPassword extends AuthError
  case object RoleNotClear extends AuthError
  case object AuthorizationTokenNotFound extends AuthError
  case object InvalidToken extends AuthError
  case object ExpiredToken extends AuthError
  case class CompanyAuthError(repoError: RepoError) extends AuthError
  case object AdminAuthError extends AuthError
}
