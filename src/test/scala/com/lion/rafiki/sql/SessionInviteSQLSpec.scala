package com.lion.rafiki.sql

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxOptionId
import com.lion.rafiki.Conf
import com.lion.rafiki.domain.company.{FormSession, SessionInvite}
import com.lion.rafiki.domain.User
import doobie.implicits._
import doobie.specs2._
import doobie.util.transactor.Transactor
import org.specs2.mutable.Specification

object SessionInviteSQLSpec extends Specification with IOChecker {

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

  import SessionInviteSQL._
  val formSessionInviteId = SessionInvite.tag(2)
  val formSessionId = FormSession.tag(2)
  val userId = User.tag(2)

  check(byIdQ(formSessionInviteId))
  check(byFormSessionQ(formSessionId))
  check(insertQ(formSessionId, userId, "/", true.some))
  check(updateQ(formSessionInviteId, userId, "/", true.some))
  check(deleteQ(formSessionInviteId))
  check(listAllQ(10, 10))
  check(listBySessionQ(formSessionId, 10, 10))
}
