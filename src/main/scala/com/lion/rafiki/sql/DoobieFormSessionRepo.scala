package com.lion.rafiki.sql

import cats.effect.MonadCancel
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.implicits.javatimedrivernative._
import cats.implicits.toFunctorOps
import com.lion.rafiki.domain.company.{Form, FormSession}
import com.lion.rafiki.domain.{Company, CompanyContract}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.{Fragments, LogHandler, Transactor}
import doobie.implicits.toSqlInterpolator
import doobie.util.meta.Meta

import org.joda.time.DateTime
import java.sql.Timestamp
private[sql] object FormSessionSQL {
  import CompanyContractSQL.given
  import CompanySQL.given
  import FormSQL.given

  given Meta[FormSession.Id] = createMetaId(FormSession)
  given Meta[DateTime] = Meta[Timestamp].timap(ts => new DateTime(ts.getTime()))(t => new Timestamp(t.getMillis()))

  def byIdQ(id: FormSession.Id) =
    sql"""SELECT id, form_id, name, start_date, end_date FROM form_sessions WHERE id=$id"""
      .query[FormSession.Record]

  def insertQ(
      companyContractId: CompanyContract.Id,
      formId: Form.Id,
      name: String,
      startDate: Option[DateTime],
      endDate: Option[DateTime]
  ) =
    sql"""INSERT INTO form_sessions (company_contract_id, form_id, name, start_date, end_date) VALUES ($companyContractId, $formId, $name, $startDate, $endDate)""".update

  def updateQ(
      id: FormSession.Id,
      formId: Form.Id,
      name: String,
      startDate: Option[DateTime],
      endDate: Option[DateTime]
  ) = {
    sql"UPDATE form_sessions SET form_id=$formId, name=$name, start_date=$startDate, end_date=$endDate WHERE id=$id".update
  }

  def deleteQ(id: FormSession.Id) =
    sql"""DELETE FROM form_sessions WHERE id=$id""".update

  val allSQL =
    fr"""SELECT id, form_id, name, start_date, end_date FROM form_sessions"""

  def getByCompanyContractQ(companyContractId: CompanyContract.Id) =
    (allSQL ++ Fragments.whereAnd(
      fr"""company_contract_id=$companyContractId"""
    )).query[FormSession.Record]

  def listByCompanyContractQ(
      companyContractId: CompanyContract.Id,
      pageSize: Int,
      offset: Int
  ) =
    paginate(pageSize: Int, offset: Int)(
      getByCompanyContractQ(companyContractId)
    )

  def listByCompanyQ(companyId: Company.Id, pageSize: Int, offset: Int) =
    paginate(pageSize: Int, offset: Int)(
      sql"""SELECT fs.id, fs.form_id, fs.name, fs.start_date, fs.end_date FROM form_sessions fs LEFT JOIN company_contracts cc ON cc.id = fs.company_contract_id WHERE cc.company = $companyId"""
        .query[FormSession.Record]
    )

  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(allSQL.query[FormSession.Record])
}

class DoobieFormSessionRepo[F[_]: TaglessMonadCancel](val xa: Transactor[F])
    extends FormSession.Repo[F] {
  import FormSessionSQL.{given, _}
  import com.lion.rafiki.domain.RepoError.ConnectionIOwithErrors

  override def create(
      formSession: FormSession.Create,
      companyContractId: CompanyContract.Id
  ): Result[FormSession.Record] =
    insertQ(
      companyContractId,
      formSession.formId,
      formSession.name,
      formSession.startDate,
      formSession.endDate
    )
      .withUniqueGeneratedKeys[FormSession.Id]("id")
      .map(formSession.withId _)
      .toResult()
      .transact(xa)

  override def update(
      formSession: FormSession.Update
  ): Result[FormSession.Record] = {
    val fSession = formSession.data
    updateQ(
      formSession.id,
      fSession.formId,
      fSession.name,
      fSession.startDate,
      fSession.endDate
    ).run
      .flatMap(_ => byIdQ(formSession.id).unique)
      .toResult()
      .transact(xa)
  }

  override def get(id: FormSession.Id): Result[FormSession.Record] =
    byIdQ(id).unique.toResult().transact(xa)

  override def delete(id: FormSession.Id): Result[Unit] = byIdQ(id).unique
    .flatMap(_ => deleteQ(id).run.as(()))
    .toResult()
    .transact(xa)

  override def list(
      pageSize: Int,
      offset: Int
  ): Result[List[FormSession.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listByCompanyContract(
      companyContractId: CompanyContract.Id,
      pageSize: Int,
      offset: Int
  ) =
    listByCompanyContractQ(companyContractId, pageSize, offset)
      .to[List]
      .toResult()
      .transact(xa)

  override def listByCompany(
      companyId: Company.Id,
      pageSize: Int,
      offset: Int
  ) =
    listByCompanyQ(companyId, pageSize, offset).to[List].toResult().transact(xa)

  override def getByCompanyContract(companyContractId: CompanyContract.Id) =
    getByCompanyContractQ(companyContractId).to[List].toResult().transact(xa)
}
