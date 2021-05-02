package com.lion.rafiki.auth

import com.lion.rafiki.domain.RepoError

sealed trait AuthError

object AuthError {
  case object RoleNotClear extends AuthError
  case object InvalidCredentials extends AuthError
  case object AuthorizationTokenNotFound extends AuthError
  case object InvalidToken extends AuthError
  case object ExpiredToken extends AuthError
  case class CompanyAuthError(repoError: RepoError) extends AuthError
  case object AdminAuthError extends AuthError
}
