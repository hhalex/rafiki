package com.lion.rafiki.sql

import doobie._
import doobie.implicits._
import Fragments.setOpt
import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits.{catsSyntaxOptionId, toFunctorOps}
import com.lion.rafiki.domain.{User}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.util.meta.Meta
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

private[sql] object UserSQL {

  implicit val fuuidReader: Meta[User.Id] = Meta[Long].imap(User.tagSerial)(_.asInstanceOf[Long])
  implicit val passwordReader: Meta[PasswordHash[BCrypt]] = Meta[String].imap(PasswordHash[BCrypt])(_.toString)
  implicit val han = LogHandler.jdkLogHandler

  def byId(id: Long) = sql"SELECT * FROM users WHERE id=$id".query[User.Record]

  def byEmail(email: String) =
    sql"SELECT * FROM users WHERE email=$email".query[User.Record]

  def insert(email: String, passwordHash: PasswordHash[BCrypt]) =
    sql"INSERT INTO users (email, password) VALUES ($email, $passwordHash)"
      .update

  def update(id: User.Id, email: Option[String], passwordHash: Option[PasswordHash[BCrypt]]) = {
    val set = setOpt(
      email.map(e => fr"email = $e"),
      passwordHash.map(p => fr"password = $p")
    )

    (fr"UPDATE users" ++ set ++ fr"WHERE id=$id")
      .update
  }

  def delete(id: User.Id) =
    sql"""DELETE FROM users WHERE id=$id"""
      .update

  def getAll(pageSize: Int, offset: Int) = paginate(pageSize, offset)(sql"SELECT * FROM users".query[User.Record])
}

class DoobieUserRepo[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
  extends User.Repo[F] {
  import UserSQL._
  override def create(user: User.CreateRecord): F[User.Record] =
    UserSQL.insert(user.username, user.password)
      .withUniqueGeneratedKeys[User.Id]("id")
      .map(user.withId _)
      .transact(xa)


  override def update(user: User.withId[Option[PasswordHash[BCrypt]]]): OptionT[F, User.Record] =
    OptionT {
      UserSQL.update(user.id, user.data.username.some, user.data.password).run
        .flatMap(_ => UserSQL.byId(user.id).option)
        .transact(xa)
    }

  override def get(id: User.Id): OptionT[F, User.Record] = OptionT(UserSQL.byId(id).option.transact(xa))

  override def delete(id: User.Id): OptionT[F, User.Record] = OptionT(UserSQL.byId(id).option)
    .semiflatMap(user => UserSQL.delete(user.id).run.as(user))
    .transact(xa)

  override def list(pageSize: Int, offset: Int): F[List[User.Record]] =
    UserSQL.getAll(pageSize: Int, offset: Int).to[List].transact(xa)

  override def findByUserName(userName: String): OptionT[F, User.Record] =
    OptionT(UserSQL.byEmail(userName).option).transact(xa)

  override def deleteByUserName(userName: String): OptionT[F, User.Record] =
    findByUserName(userName).flatMap(u => delete(u.id))
}
