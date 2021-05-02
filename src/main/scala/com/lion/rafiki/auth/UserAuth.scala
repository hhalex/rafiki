package com.lion.rafiki.auth

import cats.effect.Sync
import cats.syntax.all._
import cats.{Applicative, Monad}
import cats.data.{EitherT, Kleisli, OptionT}
import tsec.passwordhashers.jca.BCrypt
import com.lion.rafiki.domain.{Company, User}
import org.http4s.dsl._
import org.http4s.{AuthScheme, AuthedRequest, AuthedRoutes, Credentials, Request, Response, Status}
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import tsec.passwordhashers.PasswordHasher

// For admin users
class HotUserStore[F[_]: Monad](hotUsers: Seq[UserCredentials])(implicit P: PasswordHasher[F, BCrypt]) {
  // A hot user is a user generated at startup time.
  private def newHotUser(username: String, password: String)(implicit P: PasswordHasher[F, BCrypt]) = {
    Applicative[F].map2(
      username.pure[F],
      BCrypt.hashpw[F](password)
    )((_, _))
  }
  val hotUsersList = hotUsers
    .traverse(u => newHotUser(u.username, u.password))
    .map(_.toList)

  def isMember(u: User.Authed) = OptionT(hotUsersList.map(_.find(_._1 == u.email))).as(u)

  def validateCredentials(creds: UserCredentials): OptionT[F, User.Authed] = for {
    user <- OptionT(hotUsersList.map(_.find(_._1 == creds.username)))
    isValidPassword <- OptionT.liftF(BCrypt.checkpwBool[F](creds.password, user._2))
    _ <- OptionT.fromOption[F](if (isValidPassword) user._1.some else None)
  } yield User.Authed(user._1)
}

class UserAuth[F[_]: Sync](userService: User.Service[F], companyRepo: Company.Repo[F], hotUserStore: HotUserStore[F], crypto: CryptoBits) {

  type Result[T] = EitherT[F, AuthError, T]

  def validateCredentials(creds: UserCredentials): Result[User.Authed] =
    hotUserStore.validateCredentials(creds).orElse(
      userService.validateCredentials(creds.username, creds.password)
        .toOption
        .map(u => User.Authed(u.data.username))
    ).toRight[AuthError](AuthError.InvalidCredentials)

  def embedAuthHeader(r: Response[F], u: User.Authed, time: String): Response[F] = {
    val message = crypto.signToken(u.email, time)
    r.putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, message)))
  }

  def role(u: User.Authed): Result[String] =
    resolveAdmin.as("Admin").run(u)
      .orElse(resolveCompany.as("Company").run(u))

  private val auth: Kleisli[Result[*], Request[F], User.Authed] = Kleisli { (request: Request[F]) => EitherT.fromEither[F] {
    for {
      header <- request.headers.get(Authorization).toRight[AuthError](AuthError.AuthorizationTokenNotFound)
      userEmail <- crypto.validateSignedToken(header.credentials.asInstanceOf[Credentials.Token].token).toRight[AuthError](AuthError.InvalidToken)
    } yield User.Authed(userEmail)
  }}

  private val resolveAdmin: Kleisli[Result[*], User.Authed, User.Authed] =
    Kleisli { m => hotUserStore.isMember(m).toRight[AuthError](AuthError.AdminAuthError) }

  private val resolveCompany: Kleisli[Result[*], User.Authed, Company.Record] =
    Kleisli { (authed: User.Authed) =>
      companyRepo.getByUserEmail(authed.email).leftMap[AuthError](AuthError.CompanyAuthError)
    }

  val authCompany = AuthMiddleware.withFallThrough[F, Company.Record](
    Kleisli { (r: Request[F]) => (auth andThen resolveCompany).run(r).toOption }
  )
  val authAdmin = AuthMiddleware.withFallThrough[F, User.Authed](
    Kleisli { (r: Request[F]) => (auth andThen resolveAdmin).run(r).toOption }
  )
}

object UserAuth {
  def errorRoutes[F[_]: Sync]: AuthedRoutes[AuthError, F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    Kleisli { req: AuthedRequest[F, AuthError] =>
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
