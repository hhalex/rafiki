package com.lion.rafiki.sql

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxOptionId
import com.lion.rafiki.Conf
import com.lion.rafiki.auth.PasswordHasher
import com.lion.rafiki.domain.{Company, User}
import doobie.implicits._
import doobie.specs2._
import doobie.util.transactor.Transactor
import org.specs2.mutable.Specification

object UserSQLSpec extends Specification with IOChecker {

  val conf = Conf[IO]().unsafeRunSync()

  val transactor = {
    val xa = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      conf.dbUrl,
      conf.dbUser,
      conf.dbPassword,
    )
    create.allTables.transact(xa).unsafeRunSync()
    xa
  }

  import UserSQL._
  val companyId = Company.tagSerial(2)
  val userId = User.tagSerial(2)

  check(byIdQ(userId))
  check(byEmailQ("email"))
  check(insertQ("name", PasswordHasher.tagString("my password")))
  check(updateQ(userId, "email".some, None))
  check(deleteQ(userId))
  check(listAllQ(10, 10))
}
