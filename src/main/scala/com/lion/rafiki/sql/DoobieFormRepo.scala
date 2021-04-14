package com.lion.rafiki.sql

import doobie.implicits._
import cats.implicits._
import cats.data.{EitherT, OptionT}
import cats.effect.Bracket
import cats.implicits.toFunctorOps
import com.lion.rafiki.domain.Form.Record
import com.lion.rafiki.domain.{Company, Form, RepoError}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.{LogHandler, Transactor}
import doobie.free.connection.ConnectionIO
import doobie.implicits.toSqlInterpolator
import doobie.postgres.implicits.pgEnumStringOpt
import doobie.util.Read
import doobie.util.meta.Meta
import doobie.util.query.Query0

private[sql] object FormSQL {
  import CompanySQL._
  implicit val formIdReader: Meta[Form.Id] = Meta[Long].imap(Form.tagSerial)(_.asInstanceOf[Long])
  implicit val formTreeIdMeta: Meta[Form.Tree.Id] = Meta[Long].imap(Form.Tree.tagSerial)(_.asInstanceOf[Long])
  implicit val formTreeKindMeta: Meta[Form.Tree.Kind] = pgEnumStringOpt("form_tree_constr", s => Form.Tree.Kind.fromStringE(s).toOption, _.toString.toLowerCase)
  implicit val formRecordReader: Read[Form.Record] = Read[(Form.Id, Option[Company.Id], String, Option[String], Option[Form.Tree.Id], Option[Form.Tree.Kind])]
    .map({ case (id, company, name, description, tree_id, tree_kind) =>
        Form(company, name, description, tree_id.zip(tree_kind)).withId(id) })
  implicit val formTreeGroupReader: Read[Form.Tree.GroupWithKey] = Read[Form.Tree.Id].map(id => Form.Tree.GroupWithKey(id, Nil))

  implicit val han = LogHandler.jdkLogHandler

  def byIdQ(id: Form.Id) =
    sql"""SELECT * FROM forms WHERE id=$id""".query[Form.Record]

  def getTreeQuestionQ(id: Form.Tree.Id) = sql"""
     SELECT ft.id, ftq.label, ftq.text FROM form_trees ft
       LEFT JOIN form_tree_questions ftq
         ON ft.id = ftq.id AND ft.kind AND ftq.kind
       WHERE id=$id
  """.query[Form.Tree.QuestionWithKey]

  def getTreeTextQ(id: Form.Tree.Id) = sql"""
     SELECT ft.id, ftt.text FROM form_trees ft
       LEFT JOIN form_tree_texts ftt
         ON ft.id = ftt.id AND ft.kind AND ftt.kind
       WHERE id=$id
  """.query[Form.Tree.TextWithKey]

  def getTreeGroupQ(id: Form.Tree.Id) = sql"""
     SELECT ft.id FROM form_trees ft
       LEFT JOIN form_tree_groups ftg
         ON ft.id = ftq.id AND ft.kind AND ftq.kind
       WHERE id=$id
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
    sql"INSERT INTO form_trees (kind, parent_id, parent_kind) VALUES($kind,$parent_id,$parent_kind)"
      .update
  }

  def insertTreeQuestionQ(id: Form.Tree.Id, label: String, text: String) = {
    fr"INSERT INTO form_tree_questions(id,label,text) VALUES($id,$label,$text)"
      .update
  }

  def insertTreeTextQ(id: Form.Tree.Id, text: String) = {
    fr"INSERT INTO form_tree_texts(id,text) VALUES($id,$text)"
      .update
  }

  def insertTreeGroupQ(id: Form.Tree.Id) = {
    fr"INSERT INTO form_tree_groups(id) VALUES($id)"
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

  def updateTreeHeaderQ(key: Form.Tree.Key, parent: Option[Form.Tree.Key]) = {
    val (parent_id, parent_kind) = parent.unzip
    sql"UPDATE form_trees SET parent_id=$parent_id, parent_kind=$parent_kind WHERE id=${key._1} AND kind=${key._2}"
      .update
  }

  def updateTreeQuestionQ(id: Form.Tree.Id, label: String, text: String) = {
    sql"UPDATE form_tree_questions SET label=$label, text=$text WHERE id=$id"
      .update
  }

  def updateTreeTextQ(id: Form.Tree.Id, text: String) = {
    sql"UPDATE form_tree_texts SET text=$text WHERE id=$id"
      .update
  }

  def updateTreeGroupQ(id: Form.Tree.Id) = {
    sql"UPDATE form_tree_groups SET 1=1 WHERE id=$id"
      .update
  }

  def deleteQ(id: Form.Id) =
    sql"DELETE FROM forms WHERE id=$id"
      .update


  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      sql"SELECT * FROM forms".query[Form.Record]
    )
}

class DoobieFormRepo[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
  extends Form.Repo[F] {
  import FormSQL._

  implicit class ConnectionIOwithErrors[T](c: ConnectionIO[T]) {
    def toResult(): Result[T] = EitherT(c.attemptSql.transact(xa)).leftMap(RepoError.fromSQLException)
  }

  override def create(form: Form.Create): Result[Form.Record] = insertQ(form.company, form.name, form.description, None)
    .withUniqueGeneratedKeys[Form.Id]("id")
    .map(form.withId _)
    .toResult()

  override def update(form: Form.Record): Result[Form.Record] = {
    updateQ(form.id, form.data.company, form.data.name, form.data.description, form.data.tree).run
      .flatMap(_ => byIdQ(form.id).unique)
      .toResult()
  }

  override def get(id: Form.Id): Result[Form.Record] = byIdQ(id).unique.toResult()

  override def getWithTree(id: Form.Id): Result[Form.Full] = {
    for {
      form <- byIdQ(id).unique
      tree <- form.data.tree.traverse(getTreeCIO)
    } yield form.mapData(_.copy(tree = tree))
  }.toResult()

  private def getTreeCIO(key: Form.Tree.Key): ConnectionIO[Form.Tree] = {
    val (id, kind) = key
    kind match {
      case Form.Tree.Kind.Group =>
        for {
          group <- getTreeGroupQ(id).unique
          childHeaders <- getTreeChildKeysByIdQ(key).to[List]
          children <- childHeaders.traverse(getTreeCIO)
        } yield group.copy(children = children)
      case Form.Tree.Kind.Question => getTreeQuestionQ(id).unique.widen
      case Form.Tree.Kind.Text => getTreeTextQ(id).unique.widen
      case _ => throw new Exception("Can't get unknown kind of tree")
    }
  }

  override def delete(id: Form.Id): Result[Form.Record] =
    byIdQ(id).unique.flatMap { form =>
      for {
        _ <- deleteQ(form.id).run
        _ <- form.data.tree.traverse(t => deleteTreeQ(t).run)
      } yield form
    }.toResult()


  override def list(pageSize: Int, offset: Int): Result[List[Form.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult()

  def updateTreeGroupCIO(g: Form.Tree.GroupWithKey, parent: Option[Form.Tree.Key]) =
    for {
      _ <- updateTreeHeaderQ(g.key, parent).run
      _ <- updateTreeGroupQ(g.id).run
      updatedGroup <- getTreeGroupQ(g.id).unique
    } yield updatedGroup

  def updateTreeGroup(g: Form.Tree.GroupWithKey, parent: Option[Form.Tree.Key]): F[Form.Tree.GroupWithKey] =
    updateTreeGroupCIO(g, parent).transact(xa)

  def insertTreeGroupCIO(g: Form.Tree.Group, parent: Option[Form.Tree.Key]) =
    for {
      generatedId <- insertTreeHeaderQ(g.kind, parent).withUniqueGeneratedKeys[Form.Tree.Id]("id")
      _ <- insertTreeGroupQ(generatedId).run
      generatedGroup <- getTreeGroupQ(generatedId).unique
    } yield generatedGroup

  def insertTreeGroup(g: Form.Tree.Group, parent: Option[Form.Tree.Key]): F[Form.Tree.GroupWithKey] =
    insertTreeGroupCIO(g, parent).transact(xa)

  private def insertTreeTextCIO(t: Form.Tree.Text, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree.TextWithKey] =
    for {
      generatedId <- insertTreeHeaderQ(Form.Tree.Kind.Text, parent).withUniqueGeneratedKeys[Form.Tree.Id]("id")
      _ <- insertTreeTextQ(generatedId, t.text).run
      generatedText <- getTreeTextQ(generatedId).unique
    } yield generatedText

  def insertTreeText(t: Form.Tree.Text, parent: Option[Form.Tree.Key]): F[Form.Tree.TextWithKey] =
    insertTreeTextCIO(t, parent).transact(xa)

  private def updateTreeTextCIO(t: Form.Tree.TextWithKey, parent: Option[Form.Tree.Key]) =
    for {
      _ <- updateTreeHeaderQ(t.key, parent).run
      _ <- updateTreeTextQ(t.id, t.text).run
      updatedText <- getTreeTextQ(t.id).unique
    } yield updatedText

  def updateTreeText(t: Form.Tree.TextWithKey, parent: Option[Form.Tree.Key]): F[Form.Tree.TextWithKey] =
    updateTreeTextCIO(t, parent).transact(xa)

  private def insertTreeQuestionCIO(q: Form.Tree.Question, parent: Option[Form.Tree.Key]) =
    for {
      generatedId <- insertTreeHeaderQ(q.kind, parent).withUniqueGeneratedKeys[Form.Tree.Id]("id")
      _ <- insertTreeQuestionQ(generatedId, q.label, q.text).run
      generatedQuestion <- getTreeQuestionQ(generatedId).unique
    } yield generatedQuestion

  def insertTreeQuestion(q: Form.Tree.Question, parent: Option[Form.Tree.Key]): F[Form.Tree.QuestionWithKey] =
    insertTreeQuestionCIO(q, parent).transact(xa)

  private def updateTreeQuestionCIO(q: Form.Tree.QuestionWithKey, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree.QuestionWithKey] =
    for {
      _ <- updateTreeHeaderQ(q.key, parent).run
      _ <- updateTreeQuestionQ(q.id, q.label, q.text).run
      updatedQuestion <- getTreeQuestionQ(q.id).unique
    } yield updatedQuestion

  def updateTreeQuestion(q: Form.Tree.QuestionWithKey, parent: Option[Form.Tree.Key]): F[Form.Tree.QuestionWithKey] =
    updateTreeQuestionCIO(q, parent).transact(xa)

  override def syncTree(tree: Form.Tree, parent: Option[Form.Tree.Key]): Result[Form.Tree] = syncTreeRec(tree, parent).toResult()

  private def syncTreeRec(tree: Form.Tree, parent: Option[Form.Tree.Key]): ConnectionIO[Form.Tree] = {
    import Form.Tree._
    tree match {
      case g: GroupWithKey =>
        updateTreeGroupCIO(g, parent).flatMap { updatedG =>
          g.children
            .traverse(c => syncTreeRec(c, updatedG.key.some))
            .as(updatedG)
        }.widen
      case g: Group =>
        insertTreeGroupCIO(g, parent).flatMap { createdG =>
          g.children
            .traverse(syncTreeRec(_, createdG.key.some))
            .as(createdG)
        }.widen
      case t: TextWithKey => updateTreeTextCIO(t, parent).widen
      case t: Text => insertTreeTextCIO(t, parent).widen
      case q: QuestionWithKey => updateTreeQuestionCIO(q, parent).widen
      case q: Question => insertTreeQuestionCIO(q, parent).widen
    }
  }

  override def deleteTree(key: Form.Tree.Key): Result[Unit] =
    deleteTreeQ(key).run.as(()).toResult()

  override def listByCompany(company: Company.Id, pageSize: Int, offset: Int): Result[List[Record]] = byCompanyIdQ(company).to[List].toResult()
}
