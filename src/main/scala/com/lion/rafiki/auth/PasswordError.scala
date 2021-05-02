package com.lion.rafiki.auth

sealed trait PasswordError

object PasswordError {
  case class EncodingError(msg: String) extends PasswordError
  case class CheckingError(msg: String) extends PasswordError
}
