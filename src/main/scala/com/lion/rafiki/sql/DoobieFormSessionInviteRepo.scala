package com.lion.rafiki.sql

import doobie.implicits._
import doobie.implicits.javasql._
import doobie.implicits.javatimedrivernative._
import cats.effect.Bracket
import cats.implicits.{toFunctorOps, toTraverseOps}
import com.lion.rafiki.domain.Company.Id
import com.lion.rafiki.domain.CompanyContract.Id
import com.lion.rafiki.domain.company.{Form, FormSession, FormSessionInvite}
import com.lion.rafiki.domain.{Company, CompanyContract, User}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.{Fragments, Transactor}
import doobie.implicits.toSqlInterpolator
import doobie.util.meta.Meta

import java.time.Instant

private[sql] object FormSessionInviteSQL {
  import FormSessionSQL._
  import UserSQL._
  implicit val formSessionInviteIdReader: Meta[FormSessionInvite.Id] = Meta[Long].imap(FormSessionInvite.tagSerial)(_.asInstanceOf[Long])

  def byIdQ(id: FormSessionInvite.Id) =
    sql"""SELECT * FROM form_session_invites WHERE id=$id""".query[FormSessionInvite.Record]

  def insertQ(formSessionId: FormSession.Id, userId: User.Id, acceptConditions: Option[Boolean]) =
    sql"""INSERT INTO form_session_invites (form_session_id, user_id, accept_conditions) VALUES ($formSessionId, $userId, $acceptConditions)"""
      .update

  def updateQ(id: FormSessionInvite.Id, formSessionId: FormSession.Id, userId: User.Id, acceptConditions: Option[Boolean]) = {
    sql"UPDATE form_session_invites SET form_session_id=$formSessionId, form_id=$userId, accept_conditions=$acceptConditions WHERE id=$id"
      .update
  }

  def deleteQ(id: FormSessionInvite.Id) =
    sql"""DELETE FROM form_session_invites WHERE id=$id"""
      .update

  def byFormSessionQ(formSessionId: FormSession.Id) = sql"""SELECT * FROM form_session_invites WHERE form_session_id = $formSessionId""".query[FormSessionInvite.Record]

  def listBySessionQ(formSessionId: FormSession.Id, pageSize: Int, offset: Int) = paginate(pageSize: Int, offset: Int)(
    byFormSessionQ(formSessionId)
  )

  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(sql"""SELECT * FROM form_session_invites""".query[FormSessionInvite.Record])
}

class DoobieFormSessionInviteRepo[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
  extends FormSessionInvite.Repo[F] {
  import FormSessionInviteSQL._
  import com.lion.rafiki.domain.RepoError.ConnectionIOwithErrors

  override def create(formSessionInvite: FormSessionInvite[User.Id]): Result[FormSessionInvite.Record] =
    insertQ(formSessionInvite.formSession, formSessionInvite.user, formSessionInvite.acceptConditions)
      .withUniqueGeneratedKeys[FormSessionInvite.Id]("id")
      .map(formSessionInvite.withId _)
      .toResult()
      .transact(xa)

  override def update(formSessionInvite: FormSessionInvite.UpdateRecord): Result[FormSessionInvite.Record] = {
    val fSession = formSessionInvite.data
    updateQ(formSessionInvite.id, fSession.formSession, fSession.user, fSession.acceptConditions).run
      .flatMap(_ => byIdQ(formSessionInvite.id).unique)
      .toResult()
      .transact(xa)
  }

  override def get(id: FormSessionInvite.Id): Result[FormSessionInvite.Record] = byIdQ(id).unique.toResult().transact(xa)

  override def getByFormSession(id: FormSession.Id): Result[List[FormSessionInvite.Record]] =
    byFormSessionQ(id).to[List].toResult().transact(xa)

  override def delete(id: FormSessionInvite.Id): Result[Unit] = byIdQ(id).unique
    .flatMap(_ => deleteQ(id).run.as(()))
    .toResult()
    .transact(xa)

  override def list(pageSize: Int, offset: Int): Result[List[FormSessionInvite.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listByFormSession(formSessionId: FormSession.Id, pageSize: Int, offset: Int) =
    listBySessionQ(formSessionId, pageSize, offset).to[List].toResult().transact(xa)

}
