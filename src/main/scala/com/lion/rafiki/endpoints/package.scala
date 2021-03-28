package com.lion.rafiki

import com.lion.rafiki.domain.User
import org.http4s.Response
import tsec.authentication.{BearerTokenAuthenticator, SecuredRequest, TSecAuthService, TSecBearerToken}

package object endpoints {
  type Auth = TSecBearerToken[User.Id]
  type AuthHandler[F[_]] = BearerTokenAuthenticator[F, User.Id, User.Authed]
  type AuthService[F[_]] = TSecAuthService[User.Authed, Auth, F]
  type AuthEndpoint[F[_]] = PartialFunction[SecuredRequest[F, User.Authed, Auth], F[Response[F]]]

  class IdVar[A](F: Long => A) {
    def unapply(str: String) = str.toLongOption.map(F)
  }
}
