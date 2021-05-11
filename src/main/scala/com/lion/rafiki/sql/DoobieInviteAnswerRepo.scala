package com.lion.rafiki.sql

import doobie._
import doobie.implicits._
import cats.syntax.all._
import Fragments.setOpt
import cats.effect.MonadCancel
import cats.implicits.{catsSyntaxOptionId, toFunctorOps}
import com.lion.rafiki.auth.PasswordHasher
import com.lion.rafiki.domain.{RepoError, User}
import com.lion.rafiki.domain.company.{Form, InviteAnswer, SessionInvite}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.util.meta.Meta

private[sql] object InviteAnswerSQL {
  import SessionInviteSQL._

  def createOrUpdateAnswerTable(
      name: InviteAnswer.TableName,
      labels: Set[String]
  ): Update0 = {
    val labelsSql = labels
      .map { _.replace("-", "_") }
      .map { label =>
        s"${label}_numeric integer, ${label}_freetext text"
      }
      .toList
      .intercalate(", ")

    val query = s"""
    DROP TABLE IF EXISTS $name;
    CREATE TABLE $name (
        invite bigint PRIMARY KEY references form_session_invites(id),
        $labelsSql
    )"""

    println(query)
    Fragment.const(query).update
  }
}

class DoobieInviteAnswerRepo[F[_]: TaglessMonadCancel](val xa: Transactor[F])
    extends InviteAnswer.Repo[F] {
  import InviteAnswerSQL._
  import RepoError._
  import com.lion.rafiki.domain.RepoError._

  override def overrideAnswerTable(
      tableName: InviteAnswer.TableName,
      labels: Set[String]
  ): Result[Unit] = createOrUpdateAnswerTable(tableName, labels).run
    .as(())
    .toResult()
    .transact(xa)
}
