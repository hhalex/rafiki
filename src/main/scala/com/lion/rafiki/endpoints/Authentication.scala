package com.lion.rafiki.endpoints

import cats.implicits.catsSyntaxApplicativeId
import cats.syntax.all._
import cats.{Monad, MonadError}
import com.lion.rafiki.auth.Role
import com.lion.rafiki.domain.{Company, User}
import com.lion.rafiki.domain.User.Authed
import tsec.authentication.TSecAuthService
import tsec.authorization.{AuthorizationInfo, BasicRBAC}

object Authentication {
  def allRoles[F[_]](pf: AuthEndpoint[F])(implicit F: MonadError[F, Throwable], A: AuthorizationInfo[F, Role, User.Authed]): AuthService[F] =
    TSecAuthService.withAuthorization(BasicRBAC.all[F, Role, User.Authed, Auth])(pf)

  def allRolesHandler[F[_]](pf: AuthEndpoint[F])(onNotAuthorized: AuthService[F])(implicit F: MonadError[F, Throwable], A: AuthorizationInfo[F, Role, User.Authed]): AuthService[F] =
    TSecAuthService.withAuthorizationHandler(BasicRBAC.all[F, Role, User.Authed, Auth])(
      pf, onNotAuthorized.run
    )

  def adminOnly[F[_]](pf: AuthEndpoint[F])(implicit F: MonadError[F, Throwable], A: AuthorizationInfo[F, Role, User.Authed]): AuthService[F] =
    TSecAuthService.withAuthorization(BasicRBAC[F, Role, User.Authed, Auth](Role.Admin))(pf)

  def companyOnly[F[_]](pf: AuthEndpoint[F])(implicit F: MonadError[F, Throwable], A: AuthorizationInfo[F, Role, User.Authed]): AuthService[F] =
    TSecAuthService.withAuthorization(BasicRBAC[F, Role, User.Authed, Auth](Role.Company))(pf)

  def authRole[F[_]]()(implicit F: Monad[F]): AuthorizationInfo[F, Role, Authed] =
    (u: User.Authed) => u.role.pure[F]

}