package com.lion.rafiki.auth

import com.lion.rafiki.domain.RepoError

enum AuthError {
  case Password(err: PasswordError)
  case UserNotFound, InvalidPassword, RoleNotClear, AuthorizationTokenNotFound, InvalidToken, ExpiredToken
  case CompanyAuthError(repoError: RepoError)
  case EmployeeAuthError(repoError: RepoError)
  case AdminAuthError
}
