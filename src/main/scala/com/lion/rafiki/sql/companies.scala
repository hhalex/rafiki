package com.lion.rafiki.sql

import doobie.implicits.toSqlInterpolator
import doobie.util.fragments.setOpt
import doobie.util.meta.Meta
import shapeless.tag
import shapeless.tag.@@

object companies {
  type Id = Int @@ T // shapeless.tag.@@

  val tagSerial = tag[T](_: Int)
  implicit val fuuidReader: Meta[Id] = Meta[Int].imap(tagSerial)(_.asInstanceOf[Int])
  import users._

  case class T(id: Id, name: String, rh_user: users.Id)

  def getById(id: Id) =
    sql"""SELECT * FROM companies WHERE id=$id""".query[T].unique

  def insert(name: String, rh_user: users.Id) =
    sql"""INSERT INTO companies (name,rh_user) VALUES ($name,$rh_user)"""
      .update
      .withUniqueGeneratedKeys[T]("id", "name", "rh_user")

  def update(id: Id, name: Option[String]) = {
    val set = setOpt(
      name.map(n => fr"name = $n")
    )
    (fr"UPDATE companies" ++ set ++ fr"WHERE id=$id")
      .update
      .withUniqueGeneratedKeys[T]("id", "name", "rh_user")
  }

  def delete(id: Id) =
    sql"""DELETE FROM companies WHERE id=$id"""
      .update
      .withUniqueGeneratedKeys[T]("id", "name", "rh_user")

  def listAll() =
    sql"""SELECT * FROM companies"""
      .query[T]
      .to[Seq]

  def listAllWithUsers() =
    sql"""SELECT * FROM companies c INNER JOIN users u ON c.rh_user = u.id"""
      .query[(T, users.T)]
      .to[Seq]
}
