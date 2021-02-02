package com.lion.rafiki.sql

import com.lion.rafiki.auth.UserStore.{User, UserId, tagFUUIDAsUserId}
import doobie.Read
import doobie.implicits._
import doobie.postgres.implicits._
import io.chrisdavenport.fuuid.FUUID
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

import java.util.UUID

object users {
  implicit val fuuidReader: Read[UserId] = Read[UUID].map(FUUID.fromUUID).map(tagFUUIDAsUserId)
  implicit val passwordReader: Read[PasswordHash[BCrypt]] = Read[String].map(PasswordHash[BCrypt])

  def getById(id: UserId) = sql"SELECT * FROM users WHERE id=${id.toString}".query[User].option
  def getByEmail(email: String) =
    sql"SELECT * FROM users WHERE email=$email".query[User].option
}
