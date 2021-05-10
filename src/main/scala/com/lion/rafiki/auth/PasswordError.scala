package com.lion.rafiki.auth

enum PasswordError {
  case EncodingError(msg: String)
  case CheckingError(msg: String)
}
