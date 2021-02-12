package com.lion.rafiki.sql

import doobie._
import doobie.implicits._
import cats.implicits._
import Fragments.setOpt
import cats.effect.IO
import com.lion.rafiki.sql.companies.{Id, tagSerial}
import doobie.postgres.implicits._
import doobie.util.meta.Meta
import io.chrisdavenport.fuuid.FUUID
import shapeless.tag
import shapeless.tag.@@
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

object users {

  type Id = Int @@ T
  val tagSerial = tag[T](_: Int)

  implicit val fuuidReader: Meta[Id] = Meta[Int].imap(tagSerial)(_.asInstanceOf[Int])
  case class T(id: Id,
               firstname: Option[String],
               name: Option[String],
               username: String,
               password: PasswordHash[BCrypt])

  implicit val passwordReader: Meta[PasswordHash[BCrypt]] = Meta[String].imap(PasswordHash[BCrypt])(_.toString)

  def getById(id: Id) = sql"SELECT * FROM users WHERE id = $id".query[T].unique

  def getByEmail(email: String) =
    sql"SELECT * FROM users WHERE email=$email".query[T].option

  def insert(email: String, passwordHash: PasswordHash[BCrypt]) =
    sql"""INSERT INTO users(email,password) VALUES($email, $passwordHash)"""
      .update
      .withUniqueGeneratedKeys[T]("id", "firstname", "name", "email", "password")

  def update(id: Id, email: Option[String], passwordHash: Option[PasswordHash[BCrypt]]) = {
    val set = setOpt(
      email.map(e => fr"email = $e"),
      passwordHash.map(p => fr"password = $p")
    )

    (fr"UPDATE users" ++ set ++ fr"WHERE id=$id")
      .update
      .withUniqueGeneratedKeys[T]("id", "firstname", "name", "email", "password")
  }

  def delete(id: Id) =
    sql"""DELETE FROM users WHERE id=$id"""
      .update
      .withUniqueGeneratedKeys[T]("id", "firstname", "name", "email", "password")
}
