package com.lion.rafiki.sql

import doobie.implicits._
import cats.implicits._
import com.lion.rafiki.domain.{Company, User}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.Transactor
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import doobie.util.meta.Meta
import cats.Show.Shown.mat
import doobie.syntax.SqlInterpolator.SingleFragment.fromPut

private[sql] object CompanySQL {
  import UserSQL.given
  given Meta[Company.Id] = createMetaId(Company)
  given Read[Company.Full] = Read[(Company.Record, User.Record)].map {
    case (company, user) => company.mapData(_.copy(rh_user = user.data))
  }

  def byIdQ(id: Company.Id) =
    sql"""SELECT * FROM companies WHERE id=$id""".query[Company.Record]

  def byUserIdQ(id: User.Id) =
    sql"""SELECT * FROM companies WHERE rh_user=$id""".query[Company.Record]

  def byUserEmailQ(email: String) =
    sql"""SELECT * FROM companies c LEFT JOIN users u ON c.rh_user = u.id WHERE u.email = $email"""
      .query[Company.Record]

  def insertQ(name: String, rh_user: User.Id) =
    sql"""INSERT INTO companies (name,rh_user) VALUES ($name,$rh_user)""".update

  def updateQ(id: Company.Id, name: String) = {
    (sql"UPDATE companies SET name=$name WHERE id=$id").update
  }

  def deleteQ(id: Company.Id) =
    sql"""DELETE FROM companies WHERE id=$id""".update

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

class DoobieCompanyRepo[F[_]: TaglessMonadCancel](val xa: Transactor[F])
    extends Company.Repo[F] {
  import CompanySQL.{_, given}
  import com.lion.rafiki.domain.RepoError._

  override def create(company: Company.CreateRecord): Result[Company.Record] =
    insertQ(company.name, company.rh_user)
      .withUniqueGeneratedKeys[Company.Id]("id")
      .map(company.withId _)
      .toResult()
      .transact(xa)

  override def update(company: Company.Record): Result[Company.Record] = {
    updateQ(company.id, company.data.name).run
      .as(company)
      .toResult()
      .transact(xa)
  }

  override def get(id: Company.Id): Result[Company.Record] =
    byIdQ(id).option.toResult().transact(xa)

  override def delete(id: Company.Id): Result[Unit] = byIdQ(id).unique
    .flatMap(_ => deleteQ(id).run.as(()))
    .toResult()
    .transact(xa)

  override def list(pageSize: Int, offset: Int): Result[List[Company.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listWithUser(
      pageSize: Int,
      offset: Int
  ): Result[List[Company.Full]] =
    listAllWithUsersQ(pageSize: Int, offset: Int)
      .to[List]
      .toResult()
      .transact(xa)

  override def getByUser(id: User.Id): Result[Company.Record] =
    byUserIdQ(id).option.toResult().transact(xa)
  override def getByUserEmail(email: String): Result[Company.Record] =
    byUserEmailQ(email).option.toResult().transact(xa)
}
