package com.lion.rafiki.domain

import com.lion.rafiki.auth.{AuthError, PasswordError}
import com.lion.rafiki.domain.company.FormSession
import cats.data.EitherT
import cats.syntax.all._
import cats.Functor

enum ValidationError:
  case UserCredentialsIncorrect

  case NotAllowed

  case CompanyContractFull
  // form sessions
  case FormSessionBrokenState
  case FormSessionCantStart(state: FormSession.State)
  case FormSessionCantFinish(state: FormSession.State)
  case FormSessionTooFewTeamMembers(teams: List[String])

