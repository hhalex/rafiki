package com.lion.rafiki.sql

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxOptionId
import com.lion.rafiki.Conf
import com.lion.rafiki.domain.company.{FormSession, SessionInvite, QuestionAnswer}
import com.lion.rafiki.domain.User
import doobie.implicits._
import doobie.specs2._
import doobie.util.transactor.Transactor
import org.specs2.mutable.Specification

object InviteAnswerSQLSpec extends Specification with IOChecker {

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

  import InviteAnswerSQL._
  val sessionInviteId = SessionInvite.tag(2)
  val questionAnswerId = QuestionAnswer.tag(2)

  check(byIdQ(sessionInviteId))
  check(insertQ(sessionInviteId, questionAnswerId, "".some))
  check(deleteQ(sessionInviteId))
}
