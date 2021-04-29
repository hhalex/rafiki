package com.lion.rafiki.sql

import doobie._
import doobie.implicits._
import Fragments.setOpt
import cats.effect.Bracket
import cats.implicits.{catsSyntaxOptionId, toFunctorOps}
import com.lion.rafiki.domain.{RepoError, User}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.util.meta.Meta
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

private[sql] object UserSQL {

  implicit val userIdReader: Meta[User.Id] = Meta[Long].imap(User.tagSerial)(_.asInstanceOf[Long])
  implicit val passwordReader: Meta[PasswordHash[BCrypt]] = Meta[String].imap(PasswordHash[BCrypt])(_.toString)
  implicit val han = LogHandler.jdkLogHandler

  def byIdQ(id: User.Id) = sql"SELECT * FROM users WHERE id=$id".query[User.Record]

  def byEmailQ(email: String) =
    sql"SELECT * FROM users WHERE email=$email".query[User.Record]

  def insertQ(email: String, passwordHash: PasswordHash[BCrypt]) =
    sql"INSERT INTO users (email, password) VALUES ($email, $passwordHash)"
      .update

  def updateQ(id: User.Id, email: Option[String], passwordHash: Option[PasswordHash[BCrypt]]) = {
    val set = setOpt(
      email.map(e => fr"email = $e"),
      passwordHash.map(p => fr"password = $p")
    )

    (fr"UPDATE users" ++ set ++ fr"WHERE id=$id")
      .update
  }

  def deleteQ(id: User.Id) =
    sql"""DELETE FROM users WHERE id=$id"""
      .update

  def listAllQ(pageSize: Int, offset: Int) = paginate(pageSize, offset)(
    sql"SELECT * FROM users".query[User.Record]
  )
}

class DoobieUserRepo[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
  extends User.Repo[F] {
  import UserSQL._
  import RepoError._

  override def create(user: User.CreateRecord): Result[User.Record] =
    insertQ(user.username, user.password)
      .withUniqueGeneratedKeys[User.Id]("id")
      .map(user.withId _)
      .toResult()
      .transact(xa)


  override def update(user: User.withId[Option[PasswordHash[BCrypt]]]): Result[User.Record] =
      updateQ(user.id, user.data.username.some, user.data.password).run
        .flatMap(_ => byIdQ(user.id).option)
        .toResult()
        .transact(xa)

  override def get(id: User.Id): Result[User.Record] = byIdQ(id).unique.toResult().transact(xa)

  override def delete(id: User.Id): Result[Unit] = byIdQ(id).unique
    .flatMap(user => deleteQ(user.id).run.as(()))
    .toResult()
    .transact(xa)

  override def list(pageSize: Int, offset: Int): Result[List[User.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def findByUserName(userName: String): Result[User.Record] =
    byEmailQ(userName).option.toResult().transact(xa)

  override def deleteByUserName(userName: String): Result[Unit] =
    findByUserName(userName).flatMap(u => delete(u.id))
}
