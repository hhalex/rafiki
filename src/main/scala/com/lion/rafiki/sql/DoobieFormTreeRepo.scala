package com.lion.rafiki.sql

import doobie.implicits._
import doobie._
import cats.implicits._
import cats.syntax.all._
import cats.effect.MonadCancel
import cats.implicits.toFunctorOps
import com.lion.rafiki.domain.company.{Form, QuestionAnswer, QuestionAnswerP, FormTree, FormTreeP}
import com.lion.rafiki.domain.{Company, RepoError, WithId}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.{LogHandler, Transactor}
import doobie.free.connection.ConnectionIO
import doobie.implicits.toSqlInterpolator
import doobie.postgres.implicits.pgEnumStringOpt
import doobie.util.Read
import doobie.util.fragments.whereAnd
import doobie.util.meta.Meta
import doobie.util.query.Query0
import doobie.util.update.Update0

private[sql] object FormTreeSQL {

    implicit val formTreeIdMeta: Meta[FormTree.Id] = createMetaId(FormTree)
  implicit val formTreeQuestionAnswerIdMeta
      : Meta[QuestionAnswer.Id] = createMetaId(QuestionAnswer)
  implicit val formTreeKindMeta: Meta[FormTree.Kind] = pgEnumStringOpt(
    "form_tree_constr",
    s => FormTree.Kind.fromStringE(s).toOption,
    _.toString.toLowerCase
  )
    implicit val formTreeGroupReader: Read[WithId[FormTree.Id, FormTree.Group]] =
    Read[FormTree.Id].map(id => FormTree.Group(List.empty[FormTree]).withId(id))

  implicit val questionAnswerReader: Read[WithId[QuestionAnswer.Id, QuestionAnswerP]] =
    Read[
      (QuestionAnswer.Id, FormTree.Id, Option[String], Option[Int])
    ].map({
      case (id, _, label, Some(num_value)) =>
        QuestionAnswer.Numeric(label, num_value).withId(id)
      case (id, _, label, None) =>
        QuestionAnswer.FreeText(label).withId(id)
    })

  implicit val questionWithKeyReader: Read[WithId[FormTree.Id, FormTree.Question]] =
    Read[(FormTree.Id, String, String)].map({ case (id, label, text) =>
      FormTree.Question(label, text, Nil).withId(id)
    })


  def getTreeQuestionQ(id: FormTree.Id) = sql"""
     SELECT ft.id, ftq.label, ftq.text FROM form_trees ft
       LEFT JOIN form_tree_questions ftq
         ON (ft.id = ftq.id AND ft.kind = ftq.kind)
       WHERE ft.id=$id
  """.query[WithId[FormTree.Id, FormTree.Question]]

  def getQuestionAnswersQ(id: FormTree.Id) = sql"""
     SELECT * FROM form_tree_question_answers
       WHERE question_id=$id
  """.query[WithId[QuestionAnswer.Id, QuestionAnswerP]]

  def getTreeTextQ(id: FormTree.Id) = sql"""
     SELECT ft.id, ftt.text FROM form_trees ft
       LEFT JOIN form_tree_texts ftt
         ON (ft.id = ftt.id AND ft.kind = ftt.kind)
       WHERE ft.id=$id
  """.query[WithId[FormTree.Id, FormTree.Text]]

  def getTreeGroupQ(id: FormTree.Id) = sql"""
     SELECT ft.id FROM form_trees ft
       LEFT JOIN form_tree_groups ftg
         ON (ft.id = ftg.id AND ft.kind = ftg.kind)
       WHERE ft.id=$id
  """.query[WithId[FormTree.Id, FormTree.Group]]

  def getTreeChildKeysByIdQ(parent: FormTree.Key): Query0[FormTree.Key] =
    sql"""SELECT id, kind FROM form_trees WHERE parent_id=${parent._1} AND parent_kind=${parent._2}"""
      .query[FormTree.Key]

  def insertTreeHeaderQ(kind: FormTree.Kind, parent: Option[FormTree.Key]) = {
    val (parent_id, parent_kind) = parent.unzip
    sql"""INSERT INTO form_trees (kind,parent_id,parent_kind) VALUES($kind,$parent_id,$parent_kind)""".update
  }

  def insertTreeQuestionQ(id: FormTree.Id, label: String, text: String) = {
    sql"""INSERT INTO form_tree_questions(id,label,text) VALUES($id,$label,$text)""".update
  }

  def insertQuestionAnswerQ(
      question_id: FormTree.Id,
      label: Option[String],
      num_value: Option[Int]
  ) = {
    sql"""INSERT INTO form_tree_question_answers(question_id,label,num_value) VALUES($question_id,$label,$num_value)""".update
  }

  def insertTreeTextQ(id: FormTree.Id, text: String) = {
    sql"""INSERT INTO form_tree_texts(id,text) VALUES($id,$text)""".update
  }

  def insertTreeGroupQ(id: FormTree.Id) = {
    sql"INSERT INTO form_tree_groups(id) VALUES($id)".update
  }


  def deleteTreeQ(key: FormTree.Key) = {
    // On CASCADE DELETE will remove all child elements automatically
    sql"""DELETE FROM form_trees WHERE id=${key._1} AND kind=${key._2}""".update
  }

  def deleteTreeChildQ(parentKey: FormTree.Key, notIn: Seq[FormTree.Key]) = {
    // On CASCADE DELETE will remove all child elements automatically
    val (id, kind) = parentKey
    val notInFr =
      Seq(fr"(parent_id=$id AND parent_kind=$kind)") ++ notIn.map(key =>
        fr0"not(id=${key._1} AND kind=${key._2})"
      )
    (fr"DELETE FROM form_trees" ++ whereAnd(notInFr: _*)).update
  }

  def deleteQuestionAnswerQ(
      questionId: FormTree.Id,
      notIn: Seq[QuestionAnswer.Id]
  ) = {
    val notInFr =
      Seq(fr"question_id=$questionId") ++ notIn.map(id => fr0"not(id=$id)")
    (fr"DELETE FROM form_tree_question_answers" ++ whereAnd(notInFr: _*)).update
  }

  def updateTreeHeaderQ(key: FormTree.Key, parent: Option[FormTree.Key]) = {
    val (parent_id, parent_kind) = parent.unzip
    sql"UPDATE form_trees SET parent_id=$parent_id, parent_kind=$parent_kind WHERE id=${key._1} AND kind=${key._2}".update
  }

  def updateTreeQuestionQ(id: FormTree.Id, label: String, text: String) = {
    sql"UPDATE form_tree_questions SET label=$label, text=$text WHERE id=$id".update
  }

  def updateQuestionAnswerQ(
      id: QuestionAnswer.Id,
      question_id: FormTree.Id,
      label: Option[String],
      num_value: Option[Int]
  ) = {
    sql"""UPDATE form_tree_question_answers SET question_id=$question_id, label=$label, num_value=$num_value WHERE id=$id""".update
  }

  def updateTreeTextQ(id: FormTree.Id, text: String) = {
    sql"UPDATE form_tree_texts SET text=$text WHERE id=$id".update
  }

  def updateTreeGroupQ(id: FormTree.Id) = {
    sql"UPDATE form_tree_groups SET id=$id WHERE id=$id".update
  }

    def getTreeRecCIO(key: FormTree.Key): ConnectionIO[WithId[FormTree.Id, FormTreeP]] = {
    val (id, kind) = key
    kind match {
      case FormTree.Kind.Group =>
        for {
          group <- getTreeGroupQ(id).unique
          childHeaders <- getTreeChildKeysByIdQ(key).to[List]
          children <- childHeaders.traverse(getTreeRecCIO)
        } yield group.mapData(_.copy(children = children.asInstanceOf[List[FormTree]]))
      case FormTree.Kind.Question => (
        for {
          question <- getTreeQuestionQ(id).unique
          questionAnswers <- getQuestionAnswersQ(question.id).to[List]
        } yield question.mapData(_.copy(answers = questionAnswers))
      )
      case FormTree.Kind.Text => getTreeTextQ(id).unique.widen
      case _                   => throw new Exception("Can't get unknown kind of tree")
    }
  }

  def syncTree(
      tree: FormTree,
      parent: Option[FormTree.Key]
  ): ConnectionIO[FormTree.Key] = for {
    parentChildPairs <- addOrUpdateTreeNodesRec(tree, parent)
    // We must wait for the update to be over before we can delete all other removed nodes
    _ <- parentChildPairs._2.traverse(pair =>
      deleteTreeChildQ(pair._1, pair._2).run
    )
    _ <- parentChildPairs._3.traverse(pair =>
      deleteQuestionAnswerQ(pair._1, pair._2).run
    )
    // Same for question answers, we keep track of all question answers so in the end we can remove the remaining ones

  } yield parentChildPairs._1

  opaque type AccTreeNodes = List[(FormTree.Key, List[FormTree.Key])]
  opaque type AccQuestionAnswers =
    List[(FormTree.Id, List[QuestionAnswer.Id])]
  private def addOrUpdateTreeNodesRec(
      tree: FormTree,
      parent: Option[FormTree.Key]
  ): ConnectionIO[(FormTree.Key, AccTreeNodes, AccQuestionAnswers)] = {
    import FormTree._
    tree match {
      case WithId(id, g: Group) => for {
          updatedKey <- updateTreeGroupCIO(g.withId(id), parent)
          updatedChildrenKeys <- g.children.traverse(c =>
            addOrUpdateTreeNodesRec(c, updatedKey.some)
          )
          (directChilds, parentChildPairs, questionAnswers) =
            updatedChildrenKeys.unzip3
        } yield (
          updatedKey,
          (updatedKey, directChilds) :: parentChildPairs.flatten,
          questionAnswers.flatten
        )
      case WithId(id, t: Text) => updateTreeTextCIO(t.withId(id), parent).map((_, Nil, Nil))
      case WithId(id, q: Question) => for {
        question <- updateTreeQuestionCIO(q.withId(id), parent)
        answers <- q.answers.traverse(syncQuestionAnswer(_, question._1))
      } yield (question, Nil, (question._1, answers) :: Nil)
      case g: Group =>
        for {
          createdKey <- insertTreeGroupCIO(g, parent)
          childKeys <- g.children.traverse(c =>
            addOrUpdateTreeNodesRec(c, createdKey.some)
          )
          (directChilds, parentChildPairs, questionAnswers) = childKeys.unzip3
        } yield (
          createdKey,
          (createdKey, directChilds) :: parentChildPairs.flatten,
          questionAnswers.flatten
        )
      case t: Text        => insertTreeTextCIO(t, parent).map((_, Nil, Nil))
      case q: Question =>
        for {
          question <- insertTreeQuestionCIO(q, parent)
          answers <- q.answers.traverse(syncQuestionAnswer(_, question._1))
        } yield (question, Nil, (question._1, answers) :: Nil)
    }
  }

  private def syncQuestionAnswer(
      a: QuestionAnswer,
      questionId: FormTree.Id
  ): ConnectionIO[QuestionAnswer.Id] = a match {
    case QuestionAnswer.FreeText(label) =>
      insertQuestionAnswerQ(questionId, label, None)
        .withUniqueGeneratedKeys[QuestionAnswer.Id]("id")
    case QuestionAnswer.Numeric(label, value) =>
      insertQuestionAnswerQ(questionId, label, value.some)
        .withUniqueGeneratedKeys[QuestionAnswer.Id]("id")
    case WithId(id, QuestionAnswer.FreeText(label)) =>
        updateQuestionAnswerQ(id, questionId, label, None).run.as(id)
    case WithId(id, QuestionAnswer.Numeric(label, value)) =>
        updateQuestionAnswerQ(id, questionId, label, value.some).run.as(id)
  }

  def updateTreeGroupCIO(
      g: WithId[FormTree.Id, FormTree.Group],
      parent: Option[FormTree.Key]
  ): ConnectionIO[FormTree.Key] =
    for {
      _ <- updateTreeHeaderQ(g.key, parent).run
      _ <- updateTreeGroupQ(g.id).run
    } yield g.key

  def insertTreeGroupCIO(
      g: FormTree.Group,
      parent: Option[FormTree.Key]
  ): ConnectionIO[FormTree.Key] =
    for {
      generatedId <- insertTreeHeaderQ(g.kind, parent)
        .withUniqueGeneratedKeys[FormTree.Id]("id")
      _ <- insertTreeGroupQ(generatedId).run
    } yield (generatedId, g.kind)

  def insertTreeTextCIO(
      t: FormTree.Text,
      parent: Option[FormTree.Key]
  ): ConnectionIO[FormTree.Key] =
    for {
      generatedId <- insertTreeHeaderQ(FormTree.Kind.Text, parent)
        .withUniqueGeneratedKeys[FormTree.Id]("id")
      _ <- insertTreeTextQ(generatedId, t.text).run
    } yield (generatedId, t.kind)

  def updateTreeTextCIO(
      t: WithId[FormTree.Id, FormTree.Text],
      parent: Option[FormTree.Key]
  ): ConnectionIO[FormTree.Key] =
    for {
      _ <- updateTreeHeaderQ(t.key, parent).run
      _ <- updateTreeTextQ(t.id, t.data.text).run
    } yield t.key

  def insertTreeQuestionCIO(
      q: FormTree.Question,
      parent: Option[FormTree.Key]
  ): ConnectionIO[FormTree.Key] =
    for {
      generatedId <- insertTreeHeaderQ(q.kind, parent)
        .withUniqueGeneratedKeys[FormTree.Id]("id")
      _ <- insertTreeQuestionQ(generatedId, q.label, q.text).run
    } yield (generatedId, q.kind)

  def updateTreeQuestionCIO(
      q: WithId[FormTree.Id, FormTree.Question],
      parent: Option[FormTree.Key]
  ): ConnectionIO[FormTree.Key] =
    for {
      _ <- updateTreeHeaderQ(q.key, parent).run
      _ <- updateTreeQuestionQ(q.id, q.data.label, q.data.text).run
    } yield q.key

}