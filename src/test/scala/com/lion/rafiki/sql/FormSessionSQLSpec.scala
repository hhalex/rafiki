package com.lion.rafiki.sql

import cats.effect.{Blocker, IO}
import com.lion.rafiki.Conf
import com.lion.rafiki.domain.CompanyContract
import com.lion.rafiki.domain.company.{Form, FormSession}
import doobie.implicits._
import doobie.specs2._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import org.specs2.mutable.Specification

object FormSessionSQLSpec extends Specification with IOChecker {

  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)
  val conf = Conf[IO]().unsafeRunSync()

  val transactor = {
    val xa = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      conf.dbUrl,
      conf.dbUser,
      conf.dbPassword,
      Blocker.liftExecutionContext(ExecutionContexts.synchronous)
    )
    create.allTables.transact(xa).unsafeRunSync()
    xa
  }

  import FormSessionSQL._
  val companyContractId = CompanyContract.tagSerial(2)
  val formSessionId = FormSession.tagSerial(2)
  val formId = Form.tagSerial(2)

  check(byIdQ(formSessionId))
  check(insertQ(companyContractId, formId, "name", None, None))
  check(updateQ(formSessionId, companyContractId, formId, "name", None, None))
  check(deleteQ(formSessionId))
  check(listAllQ(10, 10))

}
