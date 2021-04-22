package com.lion.rafiki.sql

import cats.effect.{Blocker, IO}
import com.lion.rafiki.Conf
import com.lion.rafiki.domain.{Company, User}
import doobie.implicits._
import doobie.specs2._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import org.specs2.mutable.Specification

object CompanySQLSpec extends Specification with IOChecker {

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

  import CompanySQL._
  val companyId = Company.tagSerial(2)
  val userId = User.tagSerial(2)

  check(byIdQ(companyId))
  check(byUserIdQ(userId))
  check(insertQ("name", userId))
  check(updateQ(companyId, "name"))
  check(deleteQ(companyId))
  check(listAllQ(10, 10))
  check(listAllWithUsersQ(10, 10))

}
