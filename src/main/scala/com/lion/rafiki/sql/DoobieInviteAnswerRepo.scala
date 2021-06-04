package com.lion.rafiki.sql

import doobie._
import doobie.implicits._
import cats.syntax.all._
import Fragments.setOpt
import cats.effect.MonadCancel
import cats.implicits._
import com.lion.rafiki.auth.PasswordHasher
import com.lion.rafiki.domain.{RepoError, User}
import com.lion.rafiki.domain.company.{Form, InviteAnswer, SessionInvite}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.util.meta.Meta
import com.lion.rafiki.domain.company.QuestionAnswer
import cats.data.EitherT

private[sql] object InviteAnswerSQL {
  import SessionInviteSQL.given
  given Meta[QuestionAnswer.Id] = createMetaId(QuestionAnswer)

  def insertQ(inviteAnswerId: SessionInvite.Id, questionAnswerId: QuestionAnswer.Id, text: Option[String]) =
    sql"INSERT INTO form_session_invite_answers(invite_id, question_answer_id, text) VALUES($inviteAnswerId, $questionAnswerId, $text)".update

  def byIdQ(inviteAnswerId: SessionInvite.Id) =
    sql"SELECT invite_id, question_answer_id, text FROM form_session_invite_answers WHERE invite_id=$inviteAnswerId".query[(SessionInvite.Id, QuestionAnswer.Id, Option[String])]

  def deleteQ(inviteAnswerId: SessionInvite.Id) =
    sql"DELETE FROM form_session_invite_answers WHERE invite_id=$inviteAnswerId".update
}

class DoobieInviteAnswerRepo[F[_]: TaglessMonadCancel](val xa: Transactor[F])
    extends InviteAnswer.Repo[F] {
  import InviteAnswerSQL._
  import RepoError._

  override def create(inviteAnswer: InviteAnswer.Create, sessionInviteId: SessionInvite.Id): Result[InviteAnswer.Record] =
    inviteAnswer.values.toList.traverse({
      case (questionAnswerId, text) => insertQ(sessionInviteId, questionAnswerId, text).run
    })
      .as(inviteAnswer.withId(sessionInviteId))
      .toResult()
      .transact(xa)

  override def update(inviteAnswer: InviteAnswer.Update): Result[InviteAnswer.Record] =
    deleteQ(inviteAnswer.id).run
      .flatMap(_ => inviteAnswer.data.values.toList.traverse({
        case (questionAnswerId, text) => insertQ(inviteAnswer.id, questionAnswerId, text).run
      }))
      .as(inviteAnswer)
      .toResult()
      .transact(xa)
  override def get(id: SessionInvite.Id): Result[InviteAnswer.Record] =
      byIdQ(id).to[List].toResult().flatMap({
        case (id, questionAnswerId, text) :: tail =>
          EitherT.rightT(InviteAnswer(tail.map(t => (t._2, t._3)).toMap + (questionAnswerId -> text)).withId(id))
        case Nil => EitherT.leftT(RepoError.NotFound)
      }).transact(xa)
  override def delete(id: SessionInvite.Id): Result[Unit] =
    deleteQ(id)
      .run
      .as(())
      .toResult()
      .transact(xa)
}
