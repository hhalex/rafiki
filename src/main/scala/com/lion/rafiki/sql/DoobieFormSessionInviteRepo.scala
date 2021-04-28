package com.lion.rafiki.sql

import doobie.implicits._
import cats.effect.Bracket
import cats.implicits.toFunctorOps
import com.lion.rafiki.domain.company.{FormSession, FormSessionInvite}
import com.lion.rafiki.domain.User
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.Transactor
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import doobie.util.meta.Meta

private[sql] object FormSessionInviteSQL {
  import FormSessionSQL._
  import UserSQL._
  implicit val formSessionInviteIdReader: Meta[FormSessionInvite.Id] = Meta[Long].imap(FormSessionInvite.tagSerial)(_.asInstanceOf[Long])
  implicit val formSessionInviteFullReader: Read[FormSessionInvite.Full] = Read[(FormSessionInvite.Record, User.Record)].map({
    case (invite, user) => invite.mapData(_.copy(user = user))
  })
  val listFullInvitesFragment = fr"""SELECT * FROM form_session_invites fsi LEFT JOIN users u ON fsi.user_id = u.id"""
  def byIdQ(id: FormSessionInvite.Id) =
    (listFullInvitesFragment ++ fr"""WHERE fsi.id=$id""").query[FormSessionInvite.Full]

  def insertQ(formSessionId: FormSession.Id, userId: User.Id, acceptConditions: Option[Boolean]) =
    sql"""INSERT INTO form_session_invites (form_session_id, user_id, accept_conditions) VALUES ($formSessionId, $userId, $acceptConditions)"""
      .update

  def updateQ(id: FormSessionInvite.Id, formSessionId: FormSession.Id, userId: User.Id, acceptConditions: Option[Boolean]) = {
    sql"UPDATE form_session_invites SET form_session_id=$formSessionId, user_id=$userId, accept_conditions=$acceptConditions WHERE id=$id"
      .update
  }

  def deleteQ(id: FormSessionInvite.Id) =
    sql"""DELETE FROM form_session_invites WHERE id=$id"""
      .update

  def byFormSessionQ(formSessionId: FormSession.Id) =
    (listFullInvitesFragment ++ fr"""WHERE form_session_id = $formSessionId""").query[FormSessionInvite.Full]

  def listBySessionQ(formSessionId: FormSession.Id, pageSize: Int, offset: Int) = paginate(pageSize: Int, offset: Int)(
    byFormSessionQ(formSessionId)
  )

  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(listFullInvitesFragment.query[FormSessionInvite.Full])
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

  override def update(formSessionInvite: FormSessionInvite.UpdateRecord): Result[FormSessionInvite.Full] = {
    val invite = formSessionInvite.data
    updateQ(formSessionInvite.id, invite.formSession, invite.user, invite.acceptConditions).run
      .flatMap(_ => byIdQ(formSessionInvite.id).unique)
      .toResult()
      .transact(xa)
  }

  override def get(id: FormSessionInvite.Id): Result[FormSessionInvite.Full] = byIdQ(id).unique.toResult().transact(xa)

  override def getByFormSession(id: FormSession.Id): Result[List[FormSessionInvite.Full]] =
    byFormSessionQ(id).to[List].toResult().transact(xa)

  override def delete(id: FormSessionInvite.Id): Result[Unit] = byIdQ(id).unique
    .flatMap(_ => deleteQ(id).run.as(()))
    .toResult()
    .transact(xa)

  override def list(pageSize: Int, offset: Int): Result[List[FormSessionInvite.Full]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listByFormSession(formSessionId: FormSession.Id, pageSize: Int, offset: Int) =
    listBySessionQ(formSessionId, pageSize, offset).to[List].toResult().transact(xa)

}
