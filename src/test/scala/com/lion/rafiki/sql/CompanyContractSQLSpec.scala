package com.lion.rafiki.sql

import cats.effect.unsafe.implicits.global
import cats.effect.IO
import com.lion.rafiki.Conf
import com.lion.rafiki.domain.{Company, CompanyContract}
import doobie.implicits._
import doobie.specs2._
import doobie.util.transactor.Transactor
import org.specs2.mutable.Specification

object CompanyContractSQLSpec extends Specification with IOChecker {

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

  import CompanyContractSQL._
  val companyId = Company.tag(2)
  val companyContractId = CompanyContract.tag(2)

  check(byIdQ(companyContractId))
  check(byCompanyIdQ(companyId))
  check(insertQ(companyId, CompanyContract.Kind.UnlimitedOpen))
  check(updateQ(companyContractId, CompanyContract.Kind.Oneshot))
  check(deleteQ(companyContractId))
  check(listAllQ(10, 10))

}
