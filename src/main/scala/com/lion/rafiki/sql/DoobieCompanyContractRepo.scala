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

  def byId(id: CompanyContract.Id) =
    sql"""SELECT * FROM company_contracts WHERE id=$id""".query[CompanyContract.Record]

  def byCompanyId(id: Company.Id) =
    sql"""SELECT * FROM company_contracts WHERE company=$id""".query[CompanyContract.Record]

  def insert(company: Company.Id, kind: CompanyContract.Kind) =
    sql"""INSERT INTO company_contracts (company,kind) VALUES ($company,$kind)"""
      .update

  def update(id: CompanyContract.Id, kind: CompanyContract.Kind) = {
    (fr"UPDATE company_contracts SET kind=$kind WHERE id=$id")
      .update
  }

  def delete(id: CompanyContract.Id) =
    sql"""DELETE FROM company_contracts WHERE id=$id"""
      .update

  def listAll(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      sql"""SELECT * FROM company_contracts""".query[CompanyContract.Record]
    )

  def listByCompany(companyId: Company.Id, pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(byCompanyId(companyId))
}

class DoobieCompanyContractRepo[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
  extends CompanyContract.Repo[F] {
  import CompanyContractSQL._
  override def create(companyContract: CompanyContract.CreateRecord): F[CompanyContract.Record] = CompanyContractSQL.insert(companyContract.company, companyContract.kind)
    .withUniqueGeneratedKeys[CompanyContract.Id]("id")
    .map(companyContract.withId _)
    .transact(xa)

  override def update(company: CompanyContract.Record): OptionT[F, CompanyContract.Record] = OptionT {
    CompanyContractSQL.update(company.id, company.data.kind).run.flatMap(_ => CompanyContractSQL.byId(company.id).option)
      .transact(xa)
  }

  override def get(id: CompanyContract.Id): OptionT[F, CompanyContract.Record] = OptionT(CompanyContractSQL.byId(id).option.transact(xa))

  override def delete(id: CompanyContract.Id): OptionT[F, CompanyContract.Record] = OptionT(CompanyContractSQL.byId(id).option)
    .semiflatMap(company => CompanyContractSQL.delete(id).run.as(company))
    .transact(xa)

  override def list(pageSize: Int, offset: Int): F[List[CompanyContract.Record]] =
    CompanyContractSQL.listAll(pageSize: Int, offset: Int).to[List].transact(xa)

  override def listByCompany(id: Company.Id, pageSize: Int, offset: Int): F[List[CompanyContract.Record]] = CompanyContractSQL.byCompanyId(id).to[List].transact(xa)
}
