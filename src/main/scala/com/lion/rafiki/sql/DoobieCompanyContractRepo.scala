package com.lion.rafiki.sql

import doobie.implicits._
import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits.toFunctorOps
import com.lion.rafiki.domain.{Company, CompanyContract}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.Transactor
import doobie.implicits.toSqlInterpolator
import doobie.util.meta.Meta

private[sql] object CompanyContractSQL {
  import CompanySQL._
  implicit val companyContractIdMeta: Meta[CompanyContract.Id] = Meta[Long].imap(CompanyContract.tagSerial)(_.asInstanceOf[Long])
  implicit val companyContractKindMeta: Meta[CompanyContract.Kind] = Meta[String].imap(CompanyContract.Kind.fromString)(_.toString)

  def byIdQ(id: CompanyContract.Id) =
    sql"""SELECT * FROM company_contracts WHERE id=$id""".query[CompanyContract.Record]

  def byCompanyIdQ(id: Company.Id) =
    sql"""SELECT * FROM company_contracts WHERE company=$id""".query[CompanyContract.Record]

  def insertQ(company: Company.Id, kind: CompanyContract.Kind) =
    sql"""INSERT INTO company_contracts (company,kind) VALUES ($company,$kind)"""
      .update

  def updateQ(id: CompanyContract.Id, kind: CompanyContract.Kind) = {
    (fr"UPDATE company_contracts SET kind=$kind WHERE id=$id")
      .update
  }

  def deleteQ(id: CompanyContract.Id) =
    sql"""DELETE FROM company_contracts WHERE id=$id"""
      .update

  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      sql"""SELECT * FROM company_contracts""".query[CompanyContract.Record]
    )

  def listByCompanyQ(companyId: Company.Id, pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(byCompanyIdQ(companyId))
}

class DoobieCompanyContractRepo[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
  extends CompanyContract.Repo[F] {
  import CompanyContractSQL._
  import com.lion.rafiki.domain.RepoError.ConnectionIOwithErrors

  override def create(companyContract: CompanyContract.CreateRecord): Result[CompanyContract.Record] =
    insertQ(companyContract.company, companyContract.kind)
      .withUniqueGeneratedKeys[CompanyContract.Id]("id")
      .map(companyContract.withId _)
      .toResult()
      .transact(xa)

  override def update(company: CompanyContract.Record): Result[CompanyContract.Record] =
    updateQ(company.id, company.data.kind).run.flatMap(_ => byIdQ(company.id).unique)
      .toResult()
      .transact(xa)

  override def get(id: CompanyContract.Id): Result[CompanyContract.Record] = byIdQ(id).unique.toResult().transact(xa)

  override def delete(id: CompanyContract.Id): Result[Unit] = byIdQ(id).unique
    .flatMap(_ => deleteQ(id).run.as(()))
    .toResult()
    .transact(xa)

  override def list(pageSize: Int, offset: Int): Result[List[CompanyContract.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listByCompany(id: Company.Id, pageSize: Int, offset: Int): Result[List[CompanyContract.Record]] =
    byCompanyIdQ(id).to[List].toResult().transact(xa)
}
