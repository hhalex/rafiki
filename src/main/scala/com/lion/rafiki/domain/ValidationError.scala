package com.lion.rafiki.domain

import com.lion.rafiki.auth.{AuthError, PasswordError}
import org.http4s.DecodeFailure
import com.lion.rafiki.domain.company.FormSession

enum ValidationError:
  case UserCredentialsIncorrect

  case NotAllowed

  case CompanyContractFull
  // form sessions
  case FormSessionBrokenState
  case FormSessionCantStart(state: FormSession.State)
  case FormSessionCantFinish(state: FormSession.State)
  case FormSessionTooFewTeamMembers(teams: List[String])

  case Repo(e: RepoError)
  case Decoding(e: DecodeFailure)
  case Auth(e: AuthError)
  case Password(e: PasswordError)
end ValidationError
