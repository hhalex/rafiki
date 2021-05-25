package com.lion.rafiki.auth

import cats.effect.Sync
import cats.syntax.all._
import cats.{Applicative, Monad}
import cats.data.{EitherT, Kleisli, OptionT}
import com.lion.rafiki.domain.company.SessionInvite
import com.lion.rafiki.domain.{Company, RepoError, User, ValidationError}
import org.http4s.dsl._
import org.http4s.{AuthScheme, AuthedRequest, AuthedRoutes, Credentials, Request, Response, Status}
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware

// For admin users
class HotUserStore[F[_]: Monad](hotUsers: Seq[UserCredentials], passwordHasher: PasswordHasher[F]) {
  // A hot user is a user generated at startup time.
  private def newHotUser(username: String, password: String) = {
    Applicative[F].map2(
      username.pure[F],
      passwordHasher.hashPwd(password).value
    )((_, _))
  }
  val hotUsersList = hotUsers
    .traverse(u => newHotUser(u.username, u.password))
    .map(_.toList.collect({ case (username, Right(pass)) => (username, pass) }))

  def isMember(u: User.Authed) = OptionT(hotUsersList.map(_.find(_._1 == u.email))).as(u)

  def validateCredentials(creds: UserCredentials): EitherT[F, AuthError | PasswordError, User.Authed] = for
    user <- EitherT.fromOptionF(hotUsersList.map(_.find(_._1 == creds.username)), AuthError.UserNotFound)
    isValidPassword <- passwordHasher.checkPwd(creds.password, user._2).leftWiden
    _ <- EitherT.fromOption(if isValidPassword then user._1.some else None, AuthError.InvalidPassword)
  yield User.Authed(user._1)
}

class UserAuth[F[_]: Sync](userService: User.Service[F], companyRepo: Company.Repo[F], hotUserStore: HotUserStore[F], crypto: CryptoBits) {

  type Result[T] = EitherT[F, AuthError | PasswordError, T]

  def validateCredentials(creds: UserCredentials): EitherT[F, AuthError | PasswordError | ValidationError | RepoError, User.Authed] =
    hotUserStore.validateCredentials(creds).leftWiden
      .orElse(
        userService.validateCredentials(creds.username, creds.password).leftWiden
         .map(u => User.Authed(u.data.username))
      )

  def embedAuthHeader(r: Response[F], u: User.Authed, time: String): Response[F] = {
    val message = crypto.signToken(u.email, time)
    r.putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, message)))
  }

  def role(u: User.Authed): Result[String] = resolveAdmin.as("Admin")(u)
    .orElse(resolveCompany.as("Company")(u))
    .orElse(resolveEmployee.as("Employee")(u))

  private val auth: Kleisli[Result, Request[F], User.Authed] = Kleisli { (request: Request[F]) => EitherT.fromEither[F] {
    for
      header <- request.headers.get[Authorization].toRight[AuthError](AuthError.AuthorizationTokenNotFound)
      userEmail <- crypto.validateSignedToken(header.credentials.asInstanceOf[Credentials.Token].token).toRight[AuthError](AuthError.InvalidToken)
    yield User.Authed(userEmail)
  }}

  private val resolveAdmin: Kleisli[Result, User.Authed, User.Authed] =
    Kleisli { m => hotUserStore.isMember(m).toRight[AuthError | PasswordError](AuthError.AdminAuthError) }

  private val resolveCompany: Kleisli[Result, User.Authed, Company.Record] =
    Kleisli { (authed: User.Authed) =>
      companyRepo.getByUserEmail(authed.email).leftMap[AuthError | PasswordError](AuthError.CompanyAuthError)
    }

  private val resolveEmployee: Kleisli[Result, User.Authed, User.Id] =
    Kleisli { (authed: User.Authed) =>
      userService.getByName(authed.email).leftMap[AuthError | PasswordError](AuthError.EmployeeAuthError).map(_.id)
    }

  val authCompany = AuthMiddleware[F, Company.Record](
    Kleisli { (r: Request[F]) => (auth andThen resolveCompany).run(r).toOption }
  )
  val authAdmin = AuthMiddleware[F, User.Authed](
    Kleisli { (r: Request[F]) => (auth andThen resolveAdmin).run(r).toOption }
  )
  val authEmployee = AuthMiddleware[F, User.Id](
    Kleisli { (r: Request[F]) => (auth andThen resolveEmployee).run(r).toOption }
  )
}

object UserAuth {
  def errorRoutes[F[_]: Sync]: AuthedRoutes[AuthError, F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    Kleisli { (req: AuthedRequest[F, AuthError]) =>
      // for any requests' auth failure we return 401
      req.req match {
        case _ =>
          OptionT.pure[F](
            Response[F](Status.Unauthorized)
          )
      }
    }
  }
}
