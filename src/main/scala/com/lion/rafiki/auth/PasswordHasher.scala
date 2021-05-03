package com.lion.rafiki.auth

import cats.Applicative
import cats.data.EitherT
import cats.syntax.all._

import scala.util.{Failure, Success, Try}

trait PasswordHasher[F[_]] {
  def hashPwd(clearPassword: String): EitherT[F, PasswordError, PasswordHasher.Password]
  def checkPwd(clearPassword: String, crypted: PasswordHasher.Password): EitherT[F, PasswordError, Boolean]
}

object PasswordHasher {
  opaque type Password = String
  val tag: String => Password = _.asInstanceOf[Password]

  import com.github.t3hnar.bcrypt._

  def bcrypt[F[_]: Applicative](): PasswordHasher[F] = new PasswordHasher[F] {
    override def hashPwd(clearPassword: String) = clearPassword.bcryptSafeBounded match {
      case Success(value) => EitherT.rightT[F, PasswordError](tag(value))
      case Failure(exception) => EitherT.leftT[F, Password](PasswordError.EncodingError(exception.getMessage))
    }
    override def checkPwd(clearPassword: String, crypted: Password) = clearPassword.isBcryptedSafeBounded(crypted) match {
      case Success(bool) => EitherT.rightT[F, PasswordError](bool)
      case Failure(exception) => EitherT.leftT[F, Boolean](PasswordError.CheckingError(exception.getMessage))
    }
  }
}