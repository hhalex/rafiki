package com.lion.rafiki.sql

import cats.effect.{Blocker, IO}
import cats.implicits.catsSyntaxOptionId
import com.lion.rafiki.Conf
import com.lion.rafiki.domain.company.{FormSession, FormSessionInvite}
import com.lion.rafiki.domain.User
import doobie.implicits._
import doobie.specs2._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import org.specs2.mutable.Specification

object FormSessionInviteSQLSpec extends Specification with IOChecker {

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

  import FormSessionInviteSQL._
  val formSessionInviteId = FormSessionInvite.tagSerial(2)
  val formSessionId = FormSession.tagSerial(2)
  val userId = User.tagSerial(2)

  check(byIdQ(formSessionInviteId))
  check(byFormSessionQ(formSessionId))
  check(insertQ(formSessionId, userId, true.some))
  check(updateQ(formSessionInviteId, formSessionId, userId, true.some))
  check(deleteQ(formSessionInviteId))
  check(listAllQ(10, 10))
  check(listBySessionQ(formSessionId, 10, 10))
}
