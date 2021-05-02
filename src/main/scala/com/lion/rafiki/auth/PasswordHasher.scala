package com.lion.rafiki.auth

import cats.Applicative
import cats.data.EitherT
import cats.syntax.all._
import shapeless.tag
import shapeless.tag.@@

import scala.util.{Failure, Success, Try}

trait PasswordHasher[F[_]] {
  def hashPwd(clearPassword: String): EitherT[F, PasswordError, PasswordHasher.Password]
  def checkPwd(clearPassword: String, crypted: PasswordHasher.Password): EitherT[F, PasswordError, Boolean]
}

object PasswordHasher {
  type Password = String @@ PasswordHasher[Option]
  val tagString = tag[PasswordHasher[Option]](_: String)

  import com.github.t3hnar.bcrypt._

  def bcrypt[F[_]: Applicative](): PasswordHasher[F] = new PasswordHasher[F] {
    override def hashPwd(clearPassword: String) = clearPassword.bcryptSafeBounded match {
      case Success(value) => EitherT.rightT[F, PasswordError](tagString(value))
      case Failure(exception) => EitherT.leftT[F, Password](PasswordError.EncodingError(exception.getMessage))
    }
    override def checkPwd(clearPassword: String, crypted: Password) = clearPassword.isBcryptedSafeBounded(crypted) match {
      case Success(bool) => EitherT.rightT[F, PasswordError](bool)
      case Failure(exception) => EitherT.leftT[F, Boolean](PasswordError.CheckingError(exception.getMessage))
    }
  }
}