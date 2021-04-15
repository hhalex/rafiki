package com.lion.rafiki.sql

import doobie.implicits._
import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits.{catsSyntaxOptionId, toFunctorOps}
import com.lion.rafiki.domain.User.Id
import com.lion.rafiki.domain.{Company, User, WithId}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.{ConnectionIO, Transactor}
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import doobie.util.fragments.setOpt
import doobie.util.meta.Meta

private[sql] object CompanySQL {
  import UserSQL._
  implicit val companyIdReader: Meta[Company.Id] = Meta[Long].imap(Company.tagSerial)(_.asInstanceOf[Long])
  implicit val companyWithUserReader: Read[Company.Full] = Read[(Company.Record, User.Record)].map(pair => pair._1.mapData(_.copy(rh_user = pair._2.data)))

  def byIdQ(id: Company.Id) =
    sql"""SELECT * FROM companies WHERE id=$id""".query[Company.Record]

  def byUserIdQ(id: User.Id) =
    sql"""SELECT * FROM companies WHERE rh_user=$id""".query[Company.Record]

  def insertQ(name: String, rh_user: User.Id) =
    sql"""INSERT INTO companies (name,rh_user) VALUES ($name,$rh_user)"""
      .update

  def updateQ(id: Company.Id, name: Option[String]) = {
    val set = setOpt(name.map(n => fr"name = $n"))
    (fr"UPDATE companies" ++ set ++ fr"WHERE id=$id")
      .update
  }

  def deleteQ(id: Company.Id) =
    sql"""DELETE FROM companies WHERE id=$id"""
      .update

  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      sql"""SELECT * FROM companies""".query[Company.Record]
    )

  def listAllWithUsersQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      sql"""SELECT * FROM companies c INNER JOIN users u ON c.rh_user = u.id"""
        .query[Company.Full]
    )
}

class DoobieCompanyRepo[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
  extends Company.Repo[F] {
  import CompanySQL._
  import com.lion.rafiki.domain.RepoError.ConnectionIOwithErrors

  override def create(company: Company.CreateRecord): Result[Company.Record] = insertQ(company.name, company.rh_user)
    .withUniqueGeneratedKeys[Company.Id]("id")
    .map(company.withId _)
    .toResult()
    .transact(xa)

  override def update(company: Company.Record): Result[Company.Record] = {
    updateQ(company.id, company.data.name.some).run.flatMap(_ => byIdQ(company.id).unique)
      .toResult()
      .transact(xa)
  }

  override def get(id: Company.Id): Result[Company.Record] = byIdQ(id).unique.toResult().transact(xa)

  override def delete(id: Company.Id): Result[Unit] = byIdQ(id).unique
    .flatMap(_ => deleteQ(id).run.as(()))
    .toResult()
    .transact(xa)

  override def list(pageSize: Int, offset: Int): Result[List[Company.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listWithUser(pageSize: Int, offset: Int): Result[List[Company.Full]] =
    listAllWithUsersQ(pageSize: Int, offset: Int)
      .to[List]
      .toResult()
      .transact(xa)

  override def getByUser(id: User.Id): Result[Company.Record] = byUserIdQ(id).unique.toResult().transact(xa)
}
