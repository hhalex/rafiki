package com.lion.rafiki.sql

import cats.effect.unsafe.implicits.global
import cats.effect.IO
import com.lion.rafiki.Conf
import com.lion.rafiki.domain.{Company, CompanyContract}
import com.lion.rafiki.domain.company.{Form, FormSession}
import doobie.implicits._
import doobie.specs2._
import doobie.util.transactor.Transactor
import org.specs2.mutable.Specification

object FormSessionSQLSpec extends Specification with IOChecker {

  val conf = Conf[IO]().unsafeRunSync()

  val transactor = {
    val xa = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      conf.dbUrl,
      conf.dbUser,
      conf.dbPassword
    )
    create.allTables.transact(xa).unsafeRunSync()
    xa
  }

  import FormSessionSQL._
  val companyContractId = CompanyContract.tagSerial(2)
  val formSessionId = FormSession.tagSerial(2)
  val formId = Form.tagSerial(2)
  val companyId = Company.tagSerial(2)

  check(byIdQ(formSessionId))
  check(insertQ(companyContractId, formId, "name", None, None))
  check(updateQ(formSessionId, formId, "name", None, None))
  check(deleteQ(formSessionId))
  check(listAllQ(10, 10))
  check(listByCompanyQ(companyId, 10, 10))
  check(listByCompanyContractQ(companyContractId, 10, 10))
  check(getByCompanyContractQ(companyContractId))

}
