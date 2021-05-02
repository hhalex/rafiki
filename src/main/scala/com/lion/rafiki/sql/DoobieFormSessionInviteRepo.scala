package com.lion.rafiki.sql

import cats.effect.MonadCancel
import doobie.implicits._
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
  val listFullInvitesFragment = fr"""SELECT fsi.form_session_id, fsi.id, u.email, fsi.team, fsi.accept_conditions FROM form_session_invites fsi LEFT JOIN users u ON fsi.user_id = u.id"""
  def byIdQ(id: FormSessionInvite.Id) =
    (listFullInvitesFragment ++ fr"""WHERE fsi.id=$id""").query[(FormSession.Id, FormSessionInvite.RecordWithEmail)]

  def insertQ(formSessionId: FormSession.Id, userId: User.Id, team: String, acceptConditions: Option[Boolean]) =
    sql"""INSERT INTO form_session_invites (form_session_id, user_id, team, accept_conditions) VALUES ($formSessionId, $userId, $team, $acceptConditions)"""
      .update

  def updateQ(id: FormSessionInvite.Id, userId: User.Id, team: String, acceptConditions: Option[Boolean]) = {
    sql"UPDATE form_session_invites SET user_id=$userId, team=$team, accept_conditions=$acceptConditions WHERE id=$id"
      .update
  }

  def deleteQ(id: FormSessionInvite.Id) =
    sql"""DELETE FROM form_session_invites WHERE id=$id"""
      .update

  def byFormSessionQ(formSessionId: FormSession.Id) =
    (listFullInvitesFragment ++ fr"""WHERE form_session_id = $formSessionId""").query[(FormSession.Id, FormSessionInvite.RecordWithEmail)]

  def listBySessionQ(formSessionId: FormSession.Id, pageSize: Int, offset: Int) =
    paginate(pageSize: Int, offset: Int)(byFormSessionQ(formSessionId))

  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(listFullInvitesFragment.query[(FormSession.Id, FormSessionInvite.RecordWithEmail)])
}

class DoobieFormSessionInviteRepo[F[_]: MonadCancel[*[_], Throwable]](val xa: Transactor[F])
  extends FormSessionInvite.Repo[F] {
  import FormSessionInviteSQL._
  import com.lion.rafiki.domain.RepoError._

  override def create(formSessionInvite: FormSessionInvite[User.Id], formSessionId: FormSession.Id): Result[FormSessionInvite.Record] =
    insertQ(formSessionId, formSessionInvite.user, formSessionInvite.team, formSessionInvite.acceptConditions)
      .withUniqueGeneratedKeys[FormSessionInvite.Id]("id")
      .map(formSessionInvite.withId _)
      .toResult()
      .transact(xa)

  override def update(formSessionInvite: FormSessionInvite.UpdateRecord): Result[FormSessionInvite.Record] = {
    val invite = formSessionInvite.data
    updateQ(formSessionInvite.id, invite.user, invite.team, invite.acceptConditions).run
      .as(formSessionInvite)
      .toResult()
      .transact(xa)
  }

  override def get(id: FormSessionInvite.Id): Result[(FormSession.Id, FormSessionInvite.RecordWithEmail)] = byIdQ(id).option.toResult().transact(xa)

  override def getByFormSession(id: FormSession.Id): Result[List[FormSessionInvite.RecordWithEmail]] =
    byFormSessionQ(id).map(_._2).to[List].toResult().transact(xa)

  override def delete(id: FormSessionInvite.Id): Result[Unit] = byIdQ(id).option
    .flatMap(_ => deleteQ(id).run.as(()))
    .toResult()
    .transact(xa)

  override def list(pageSize: Int, offset: Int): Result[List[(FormSession.Id, FormSessionInvite.RecordWithEmail)]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listByFormSession(formSessionId: FormSession.Id, pageSize: Int, offset: Int) =
    listBySessionQ(formSessionId, pageSize, offset).map(_._2).to[List].toResult().transact(xa)

}
