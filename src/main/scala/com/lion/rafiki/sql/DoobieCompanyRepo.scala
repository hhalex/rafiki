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

  def byId(id: Company.Id) =
    sql"""SELECT * FROM companies WHERE id=$id""".query[Company.Record]

  def byUserId(id: User.Id) =
    sql"""SELECT * FROM companies WHERE rh_user=$id""".query[Company.Record]

  def insert(name: String, rh_user: User.Id) =
    sql"""INSERT INTO companies (name,rh_user) VALUES ($name,$rh_user)"""
      .update

  def update(id: Company.Id, name: Option[String]) = {
    val set = setOpt(name.map(n => fr"name = $n"))
    (fr"UPDATE companies" ++ set ++ fr"WHERE id=$id")
      .update
  }

  def delete(id: Company.Id) =
    sql"""DELETE FROM companies WHERE id=$id"""
      .update

  def listAll(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      sql"""SELECT * FROM companies""".query[Company.Record]
    )

  def listAllWithUsers(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      sql"""SELECT * FROM companies c INNER JOIN users u ON c.rh_user = u.id"""
        .query[Company.Full]
    )
}

class DoobieCompanyRepo[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
  extends Company.Repo[F] {
  import CompanySQL._
  override def create(company: Company.CreateRecord): F[Company.Record] = CompanySQL.insert(company.name, company.rh_user)
    .withUniqueGeneratedKeys[Company.Id]("id")
    .map(company.withId _)
    .transact(xa)

  override def update(company: Company.Record): OptionT[F, Company.Record] = OptionT {
    CompanySQL.update(company.id, company.data.name.some).run.flatMap(_ => CompanySQL.byId(company.id).option)
      .transact(xa)
  }

  override def get(id: Company.Id): OptionT[F, Company.Record] = OptionT(CompanySQL.byId(id).option.transact(xa))

  override def delete(id: Company.Id): OptionT[F, Company.Record] = OptionT(CompanySQL.byId(id).option)
    .semiflatMap(company => CompanySQL.delete(id).run.as(company))
    .transact(xa)

  override def list(pageSize: Int, offset: Int): F[List[Company.Record]] =
    CompanySQL.listAll(pageSize: Int, offset: Int).to[List].transact(xa)

  override def listWithUser(pageSize: Int, offset: Int): F[List[Company.Full]] =
    CompanySQL.listAllWithUsers(pageSize: Int, offset: Int)
      .to[List].transact(xa)

  override def getByUser(id: User.Id): OptionT[F, Company.Record] = OptionT(CompanySQL.byUserId(id).option.transact(xa))
}
