package com.lion.rafiki.sql

import cats.effect.MonadCancel
import doobie.implicits._
import cats.implicits.toFunctorOps
import com.lion.rafiki.domain.company.{FormSession, SessionInvite}
import com.lion.rafiki.domain.User
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.Transactor
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import doobie.util.meta.Meta

private[sql] object SessionInviteSQL {
  import FormSessionSQL.given
  import UserSQL.given
  given Meta[SessionInvite.Id] = createMetaId(SessionInvite)
  given Read[SessionInvite.Full] = Read[(SessionInvite.Record, User.Record)].map {
    case (invite, user) => invite.mapData(_.copy(user = user))
  }

  val listFullInvitesFragment =
    fr"""SELECT fsi.form_session_id, fsi.id, u.email, fsi.team, fsi.accept_conditions FROM form_session_invites fsi LEFT JOIN users u ON fsi.user_id = u.id"""
  def byIdQ(id: SessionInvite.Id) =
    (listFullInvitesFragment ++ fr"""WHERE fsi.id=$id""")
      .query[(FormSession.Id, SessionInvite.RecordWithEmail)]

  def insertQ(
      formSessionId: FormSession.Id,
      userId: User.Id,
      team: String,
      acceptConditions: Option[Boolean]
  ) =
    sql"""INSERT INTO form_session_invites (form_session_id, user_id, team, accept_conditions) VALUES ($formSessionId, $userId, $team, $acceptConditions)""".update

  def updateQ(
      id: SessionInvite.Id,
      userId: User.Id,
      team: String,
      acceptConditions: Option[Boolean]
  ) = {
    sql"UPDATE form_session_invites SET user_id=$userId, team=$team, accept_conditions=$acceptConditions WHERE id=$id".update
  }

  def deleteQ(id: SessionInvite.Id) =
    sql"""DELETE FROM form_session_invites WHERE id=$id""".update

  def byFormSessionQ(formSessionId: FormSession.Id) =
    (listFullInvitesFragment ++ fr"""WHERE form_session_id = $formSessionId""")
      .query[(FormSession.Id, SessionInvite.RecordWithEmail)]

  def byUserEmailQ(email: String) =
    (listFullInvitesFragment ++ fr"""WHERE email = $email""")
      .query[(FormSession.Id, SessionInvite.RecordWithEmail)]

  def byUserIdQ(userId: User.Id) =
    (listFullInvitesFragment ++ fr"""WHERE user_id = $userId""")
      .query[(FormSession.Id, SessionInvite.RecordWithEmail)]

  def listBySessionQ(
      formSessionId: FormSession.Id,
      pageSize: Int,
      offset: Int
  ) =
    paginate(pageSize: Int, offset: Int)(byFormSessionQ(formSessionId))

  def listByUserIdQ(
      userId: User.Id,
      pageSize: Int,
      offset: Int
  ) =
    paginate(pageSize: Int, offset: Int)(byUserIdQ(userId))

  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      listFullInvitesFragment
        .query[(FormSession.Id, SessionInvite.RecordWithEmail)]
    )
}

class DoobieSessionInviteRepo[F[_]: TaglessMonadCancel](
    val xa: Transactor[F]
) extends SessionInvite.Repo[F] {
  import SessionInviteSQL.{given, _}
  import com.lion.rafiki.domain.RepoError._

  override def create(
      sessionInvite: SessionInvite[User.Id],
      formSessionId: FormSession.Id
  ): Result[SessionInvite.Record] =
    insertQ(
      formSessionId,
      sessionInvite.user,
      sessionInvite.team,
      sessionInvite.acceptConditions
    )
      .withUniqueGeneratedKeys[SessionInvite.Id]("id")
      .map(sessionInvite.withId _)
      .toResult()
      .transact(xa)

  override def update(
      sessionInvite: SessionInvite.UpdateRecord
  ): Result[SessionInvite.Record] = {
    val invite = sessionInvite.data
    updateQ(
      sessionInvite.id,
      invite.user,
      invite.team,
      invite.acceptConditions
    ).run
      .as(sessionInvite)
      .toResult()
      .transact(xa)
  }

  override def get(
      id: SessionInvite.Id
  ): Result[(FormSession.Id, SessionInvite.RecordWithEmail)] =
    byIdQ(id).option.toResult().transact(xa)

  override def getByUserEmail(email: String): Result[List[(FormSession.Id, SessionInvite.RecordWithEmail)]] =
    byUserEmailQ(email).to[List].toResult().transact(xa)

  override def getByUserId(userId: User.Id): Result[List[(FormSession.Id, SessionInvite.RecordWithEmail)]] =
    byUserIdQ(userId).to[List].toResult().transact(xa)

  override def getByFormSession(
      id: FormSession.Id
  ): Result[List[SessionInvite.RecordWithEmail]] =
    byFormSessionQ(id).map(_._2).to[List].toResult().transact(xa)

  override def delete(id: SessionInvite.Id): Result[Unit] = byIdQ(id).option
    .flatMap(_ => deleteQ(id).run.as(()))
    .toResult()
    .transact(xa)

  override def list(
      pageSize: Int,
      offset: Int
  ): Result[List[(FormSession.Id, SessionInvite.RecordWithEmail)]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listByFormSession(
      formSessionId: FormSession.Id,
      pageSize: Int,
      offset: Int
  ) =
    listBySessionQ(formSessionId, pageSize, offset)
      .map(_._2)
      .to[List]
      .toResult()
      .transact(xa)

  override def listByUserId(
      userId: User.Id,
      pageSize: Int,
      offset: Int
  ) =
    listByUserIdQ(userId, pageSize, offset)
      .to[List]
      .toResult()
      .transact(xa)

}
