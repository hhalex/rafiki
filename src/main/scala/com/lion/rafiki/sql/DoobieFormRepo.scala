package com.lion.rafiki.sql

import doobie.implicits._
import cats.implicits._
import cats.effect.Bracket
import cats.implicits.toFunctorOps
import com.lion.rafiki.domain.company.Form
import com.lion.rafiki.domain.{Company, RepoError}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.{LogHandler, Transactor}
import doobie.free.connection.ConnectionIO
import doobie.implicits.toSqlInterpolator
import doobie.postgres.implicits.pgEnumStringOpt
import doobie.util.Read
import doobie.util.fragments.whereAnd
import doobie.util.meta.Meta
import doobie.util.query.Query0

private[sql] object FormSQL {
  import CompanySQL._
  implicit val formIdReader: Meta[Form.Id] = Meta[Long].imap(Form.tagSerial)(_.asInstanceOf[Long])
  implicit val formTreeIdMeta: Meta[Form.Tree.Id] = Meta[Long].imap(Form.Tree.tagSerial)(_.asInstanceOf[Long])
  implicit val formTreeQuestionAnswerIdMeta: Meta[Form.Tree.Question.Answer.Id] = Meta[Long].imap(Form.Tree.Question.Answer.tagSerial)(_.asInstanceOf[Long])
  implicit val formTreeKindMeta: Meta[Form.Tree.Kind] = pgEnumStringOpt("form_tree_constr", s => Form.Tree.Kind.fromStringE(s).toOption, _.toString.toLowerCase)
  implicit val formRecordReader: Read[Form.Record] = Read[(Form.Id, Option[Company.Id], String, Option[String], Option[Form.Tree.Id], Option[Form.Tree.Kind])]
    .map({ case (id, company, name, description, tree_id, tree_kind) =>
        Form(company, name, description, tree_id.zip(tree_kind)).withId(id) })
  implicit val formTreeGroupReader: Read[Form.Tree.GroupWithKey] = Read[Form.Tree.Id].map(id => Form.Tree.GroupWithKey(id, Nil))

  implicit val questionAnswerReader: Read[Form.Tree.Question.AnswerWithId] = Read[(Form.Tree.Question.Answer.Id, Form.Tree.Id, Option[String], Option[Int])].map({
    case (id, _, label, Some(num_value)) => Form.Tree.Question.Answer.NumericWithId(id, label, num_value)
    case (id, _, label, None) => Form.Tree.Question.Answer.FreeTextWithId(id, label)
  })

  implicit val questionWithKeyReader: Read[Form.Tree.QuestionWithKey] = Read[(Form.Tree.Id, String, String)].map({
    case (id, label, text) => Form.Tree.QuestionWithKey(id, label, text, Nil)
  })

  implicit val han = LogHandler.jdkLogHandler

  def byIdQ(id: Form.Id) =
    sql"""SELECT * FROM forms WHERE id=$id""".query[Form.Record]

  def getTreeQuestionQ(id: Form.Tree.Id) = sql"""
     SELECT ft.id, ftq.label, ftq.text FROM form_trees ft
       LEFT JOIN form_tree_questions ftq
         ON (ft.id = ftq.id AND ft.kind = ftq.kind)
       WHERE ft.id=$id
  """.query[Form.Tree.QuestionWithKey]

  def getQuestionAnswersQ(id: Form.Tree.Id) = sql"""
     SELECT * FROM form_tree_question_answers
       WHERE question_id=$id
  """.query[Form.Tree.Question.AnswerWithId]

  def getTreeTextQ(id: Form.Tree.Id) = sql"""
     SELECT ft.id, ftt.text FROM form_trees ft
       LEFT JOIN form_tree_texts ftt
         ON (ft.id = ftt.id AND ft.kind = ftt.kind)
       WHERE ft.id=$id
  """.query[Form.Tree.TextWithKey]

  def getTreeGroupQ(id: Form.Tree.Id) = sql"""
     SELECT ft.id FROM form_trees ft
       LEFT JOIN form_tree_groups ftg
         ON (ft.id = ftg.id AND ft.kind = ftg.kind)
       WHERE ft.id=$id
  """.query[Form.Tree.GroupWithKey]

  def getTreeChildKeysByIdQ(parent: Form.Tree.Key): Query0[Form.Tree.Key] =
    sql"""SELECT id, kind FROM form_trees WHERE parent_id=${parent._1} AND parent_kind=${parent._2}""".query[Form.Tree.Key]

  def byCompanyIdQ(id: Company.Id) =
    sql"""SELECT * FROM forms WHERE company=$id OR company=NULL""".query[Form.Record]

  def insertQ(company: Option[Company.Id], name: String, description: Option[String], tree: Option[Form.Tree.Key]) = {
    val (tree_id, tree_kind) = tree.unzip
    sql"""INSERT INTO forms (company,name,description,tree_id,tree_kind) VALUES ($company,$name,$description,$tree_id,$tree_kind)"""
      .update
  }

  def insertTreeHeaderQ(kind: Form.Tree.Kind, parent: Option[Form.Tree.Key]) = {
    val (parent_id, parent_kind) = parent.unzip
    sql"""INSERT INTO form_trees (kind,parent_id,parent_kind) VALUES($kind,$parent_id,$parent_kind)"""
      .update
  }

  def insertTreeQuestionQ(id: Form.Tree.Id, label: String, text: String) = {
    sql"""INSERT INTO form_tree_questions(id,label,text) VALUES($id,$label,$text)"""
      .update
  }

  def insertQuestionAnswerQ(question_id: Form.Tree.Id, label: Option[String], num_value: Option[Int]) = {
    sql"""INSERT INTO form_tree_question_answers(question_id,label,num_value) VALUES($question_id,$label,$num_value)"""
      .update
  }

  def insertTreeTextQ(id: Form.Tree.Id, text: String) = {
    sql"""INSERT INTO form_tree_texts(id,text) VALUES($id,$text)"""
      .update
  }

  def insertTreeGroupQ(id: Form.Tree.Id) = {
    sql"INSERT INTO form_tree_groups(id) VALUES($id)"
      .update
  }

  def updateQ(id: Form.Id, company: Option[Company.Id], name: String, description: Option[String], tree: Option[Form.Tree.Key]) = {
    val (tree_id, tree_kind) = tree.unzip
    sql"""UPDATE forms SET company=$company, name=$name, description=$description, tree_id=$tree_id, tree_kind=$tree_kind WHERE id=$id"""
      .update
  }

  def deleteTreeQ(key: Form.Tree.Key) = {
    // On CASCADE DELETE will remove all child elements automatically
    sql"""DELETE FROM form_trees WHERE id=${key._1} AND kind=${key._2}"""
      .update
  }

  def deleteTreeChildQ(parentKey: Form.Tree.Key, notIn: Seq[Form.Tree.Key]) = {
    // On CASCADE DELETE will remove all child elements automatically
    val (id, kind) = parentKey
    val notInFr = Seq(fr"(parent_id=$id AND parent_kind=$kind)") ++ notIn.map(key => fr0"not(id=${key._1} AND kind=${key._2})")
    (fr"DELETE FROM form_trees" ++ whereAnd(notInFr: _*))
      .update
  }

  def deleteQuestionAnswerQ(questionId: Form.Tree.Id, notIn: Seq[Form.Tree.Question.Answer.Id]) = {
    val notInFr = Seq(fr"question_id=$questionId") ++ notIn.map(id => fr0"not(id=$id)")
    (fr"DELETE FROM form_tree_question_answers" ++ whereAnd(notInFr: _*))
      .update
  }

  def updateTreeHeaderQ(key: Form.Tree.Key, parent: Option[Form.Tree.Key]) = {
    val (parent_id, parent_kind) = parent.unzip
    sql"UPDATE form_trees SET parent_id=$parent_id, parent_kind=$parent_kind WHERE id=${key._1} AND kind=${key._2}"
      .update
  }

  def updateTreeQuestionQ(id: Form.Tree.Id, label: String, text: String) = {
    sql"UPDATE form_tree_questions SET label=$label, text=$text WHERE id=$id"
      .update
  }

  def updateQuestionAnswerQ(id: Form.Tree.Question.Answer.Id, question_id: Form.Tree.Id, label: Option[String], num_value: Option[Int]) = {
    sql"""UPDATE form_tree_question_answers SET question_id=$question_id, label=$label, num_value=$num_value WHERE id=$id"""
      .update
  }

  def updateTreeTextQ(id: Form.Tree.Id, text: String) = {
    sql"UPDATE form_tree_texts SET text=$text WHERE id=$id"
      .update
  }

  def updateTreeGroupQ(id: Form.Tree.Id) = {
    sql"UPDATE form_tree_groups SET id=$id WHERE id=$id"
      .update
  }

  def deleteQ(id: Form.Id) =
    sql"DELETE FROM forms WHERE id=$id"
      .update

  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      sql"SELECT * FROM forms".query[Form.Record]
    )

  def getCIO(id: Form.Id): ConnectionIO[Form.Full] = for {
    form <- byIdQ(id).unique
    tree <- form.data.tree.traverse(getTreeRecCIO)
  } yield form.mapData(_.copy(tree = tree))

  def getTreeRecCIO(key: Form.Tree.Key): ConnectionIO[Form.TreeWithKey] = {
    val (id, kind) = key
    kind match {
      case Form.Tree.Kind.Group =>
        for {
          group <- getTreeGroupQ(id).unique
          childHeaders <- getTreeChildKeysByIdQ(key).to[List]
          children <- childHeaders.traverse(getTreeRecCIO)
        } yield group.copy(children = children)
      case Form.Tree.Kind.Question => (for {
        question <- getTreeQuestionQ(id).unique
        questionAnswers <- getQuestionAnswersQ(question.id).to[List]
      } yield question.copy(answers = questionAnswers))
      case Form.Tree.Kind.Text => getTreeTextQ(id).unique.widen
      case _ => throw new Exception("Can't get unknown kind of tree")
    }
  }

  def syncTree(tree: Form.Tree, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree.Key] = for {
    parentChildPairs <- addOrUpdateTreeNodesRec(tree, parent)
    // We must wait for the update to be over before we can delete all other removed nodes
    _ <- parentChildPairs._2.traverse(pair => deleteTreeChildQ(pair._1, pair._2).run)
    _ <- parentChildPairs._3.traverse(pair => deleteQuestionAnswerQ(pair._1, pair._2).run)
    // Same for question answers, we keep track of all question answers so in the end we can remove the remaining ones

  } yield parentChildPairs._1

  type AccTreeNodes =  List[(Form.Tree.Key, List[Form.Tree.Key])]
  type AccQuestionAnswers =  List[(Form.Tree.Id, List[Form.Tree.Question.Answer.Id])]
  private def addOrUpdateTreeNodesRec(tree: Form.Tree, parent: Option[Form.Tree.Key]): ConnectionIO[(Form.Tree.Key, AccTreeNodes, AccQuestionAnswers)] = {
    import Form.Tree._
    tree match {
      case g: GroupWithKey => for {
        updatedKey <- updateTreeGroupCIO(g, parent)
        updatedChildrenKeys <- g.children.traverse(c => addOrUpdateTreeNodesRec(c, updatedKey.some))
        (directChilds, parentChildPairs, questionAnswers) = updatedChildrenKeys.unzip3
      } yield (updatedKey, (updatedKey, directChilds) :: parentChildPairs.flatten, questionAnswers.flatten)
      case g: Group => for {
        createdKey <- insertTreeGroupCIO(g, parent)
        childKeys <- g.children.traverse(c => addOrUpdateTreeNodesRec(c, createdKey.some))
        (directChilds, parentChildPairs, questionAnswers) = childKeys.unzip3
      } yield (createdKey, (createdKey, directChilds) :: parentChildPairs.flatten, questionAnswers.flatten)
      case t: TextWithKey => updateTreeTextCIO(t, parent).map((_, Nil, Nil))
      case t: Text => insertTreeTextCIO(t, parent).map((_, Nil, Nil))
      case q: QuestionWithKey => for {
        question <- updateTreeQuestionCIO(q, parent)
        answers <- q.answers.traverse(syncQuestionAnswer(_, question._1))
      } yield (question, Nil, (question._1, answers) :: Nil)
      case q: Question => for {
        question <- insertTreeQuestionCIO(q, parent)
        answers <- q.answers.traverse(syncQuestionAnswer(_, question._1))
      } yield (question, Nil, (question._1, answers) :: Nil)
    }
  }

  private def syncQuestionAnswer(a: Form.Tree.Question.Answer, questionId: Form.Tree.Id): ConnectionIO[Form.Tree.Question.Answer.Id] = a match {
    case Form.Tree.Question.Answer.FreeText(label) =>
      insertQuestionAnswerQ(questionId, label, None)
        .withUniqueGeneratedKeys[Form.Tree.Question.Answer.Id]("id")
    case Form.Tree.Question.Answer.FreeTextWithId(id, label) =>
      updateQuestionAnswerQ(id, questionId, label, None).run.as(id)
    case Form.Tree.Question.Answer.NumericWithId(id, label, value) =>
      updateQuestionAnswerQ(id, questionId, label, value.some).run.as(id)
    case Form.Tree.Question.Answer.Numeric(label, value) =>
      insertQuestionAnswerQ(questionId, label, value.some)
        .withUniqueGeneratedKeys[Form.Tree.Question.Answer.Id]("id")
  }

  def updateTreeGroupCIO(g: Form.Tree.GroupWithKey, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree.Key] =
    for {
      _ <- updateTreeHeaderQ(g.key, parent).run
      _ <- updateTreeGroupQ(g.id).run
    } yield g.key

  def insertTreeGroupCIO(g: Form.Tree.Group, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree.Key] =
    for {
      generatedId <- insertTreeHeaderQ(g.kind, parent).withUniqueGeneratedKeys[Form.Tree.Id]("id")
      _ <- insertTreeGroupQ(generatedId).run
    } yield (generatedId, g.kind)

  def insertTreeTextCIO(t: Form.Tree.Text, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree.Key] =
    for {
      generatedId <- insertTreeHeaderQ(Form.Tree.Kind.Text, parent).withUniqueGeneratedKeys[Form.Tree.Id]("id")
      _ <- insertTreeTextQ(generatedId, t.text).run
    } yield  (generatedId, t.kind)

  def updateTreeTextCIO(t: Form.Tree.TextWithKey, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree.Key] =
    for {
      _ <- updateTreeHeaderQ(t.key, parent).run
      _ <- updateTreeTextQ(t.id, t.text).run
    } yield t.key

  def insertTreeQuestionCIO(q: Form.Tree.Question, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree.Key] =
    for {
      generatedId <- insertTreeHeaderQ(q.kind, parent).withUniqueGeneratedKeys[Form.Tree.Id]("id")
      _ <- insertTreeQuestionQ(generatedId, q.label, q.text).run
    } yield (generatedId, q.kind)

  def updateTreeQuestionCIO(q: Form.Tree.QuestionWithKey, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree.Key] =
    for {
      _ <- updateTreeHeaderQ(q.key, parent).run
      _ <- updateTreeQuestionQ(q.id, q.label, q.text).run
    } yield q.key

}

class DoobieFormRepo[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
  extends Form.Repo[F] {
  import FormSQL._
  import RepoError.ConnectionIOwithErrors

  override def create(form: Form.Create): Result[Form.Full] = (for {
    createdTreeKey <- form.tree.traverse(t => syncTree(t, None))
    id <- insertQ(form.company, form.name, form.description, createdTreeKey)
      .withUniqueGeneratedKeys[Form.Id]("id")
    createdForm <- getCIO(id)
  } yield createdForm).toResult().transact(xa)

  override def update(form: Form.Update): Result[Form.Full] = (for {
    updateTreeKey <- form.data.tree.traverse(t => syncTree(t, None))
    _ <- updateQ(form.id, form.data.company, form.data.name, form.data.description, updateTreeKey).run
    updatedForm <- getCIO(form.id)
  } yield updatedForm).toResult().transact(xa)

  override def get(id: Form.Id): Result[Form.Full] = getCIO(id).toResult().transact(xa)

  override def delete(id: Form.Id): Result[Unit] = (for {
    form <- byIdQ(id).unique
    _ <- deleteQ(form.id).run
    _ <- form.data.tree.traverse(t => deleteTreeQ(t).run)
  } yield ()).toResult().transact(xa)


  override def list(pageSize: Int, offset: Int): Result[List[Form.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listByCompany(company: Company.Id, pageSize: Int, offset: Int): Result[List[Form.Record]] =
    byCompanyIdQ(company).to[List].toResult().transact(xa)
}
