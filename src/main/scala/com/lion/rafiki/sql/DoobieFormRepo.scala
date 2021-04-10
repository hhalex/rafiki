package com.lion.rafiki.sql

import doobie.implicits._
import cats.implicits._
import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits.{catsSyntaxApplicativeId, toFunctorOps}
import com.lion.rafiki.domain.Form.Record
import com.lion.rafiki.domain.{Company, Form}
import com.lion.rafiki.sql.SQLPagination.paginate
import doobie.Transactor
import doobie.free.connection.ConnectionIO
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import doobie.util.meta.Meta
import doobie.util.query.Query0

private[sql] object FormSQL {
  import CompanySQL._
  implicit val formIdReader: Meta[Form.Id] = Meta[Long].imap(Form.tagSerial)(_.asInstanceOf[Long])
  implicit val formTreeIdMeta: Meta[Form.Tree.Id] = Meta[Long].imap(Form.Tree.tagSerial)(_.asInstanceOf[Long])
  implicit val formTreeKindMeta: Meta[Form.Tree.Kind] = Meta[String].imap(Form.Tree.Kind.fromString)(_.toString.toLowerCase)
  implicit val formRecordReader: Read[Form.Record] = Read[(Form.Id, Option[Company.Id], String, Option[String], Option[Form.Tree.Id], Option[Form.Tree.Kind])]
    .map({ case (id, company, name, description, tree_id, tree_kind) =>
        Form(company, name, description, tree_id.zip(tree_kind)).withId(id) })
  implicit val formTreeGroupReader: Read[Form.Tree.GroupWithKey] = Read[Form.Tree.Id].map(id => Form.Tree.GroupWithKey(id, Nil))

  def byIdQ(id: Form.Id) =
    sql"""SELECT * FROM forms WHERE id=$id""".query[Form.Record]

  def getTreeNodeByIdQ(key: Form.Tree.Key): Query0[Form.Tree.Record] = key._2 match {
    case Form.Tree.Kind.Question =>
      sql"""
         SELECT ft.id, ftq.label, ftq.text FROM form_trees ft
           LEFT JOIN form_tree_questions ftq
             ON ft.id = ftq.id AND ft.kind AND ftq.kind
           WHERE id=${key._1}
      """.query[Form.Tree.QuestionWithKey].widen
    case Form.Tree.Kind.Text =>
      sql"""
         SELECT ft.id, ftt.text FROM form_trees ft
           LEFT JOIN form_tree_texts ftt
             ON ft.id = ftt.id AND ft.kind AND ftt.kind
           WHERE id=${key._1}
      """.query[Form.Tree.TextWithKey].widen
    case Form.Tree.Kind.Group =>
      sql"""
         SELECT ft.id FROM form_trees ft
           LEFT JOIN form_tree_groups ftg
             ON ft.id = ftq.id AND ft.kind AND ftq.kind
           WHERE id=${key._1}
      """.query[Form.Tree.GroupWithKey].widen

    case Form.Tree.Kind.Unknown(s) => throw new Exception(s"Unknow form tree kind $s")
  }

  def getNodeHeadersByParentIdQ(id: Form.Tree.Id, kind: Form.Tree.Kind): Query0[Form.Tree.Key] =
    sql"""SELECT id, kind FROM form_trees WHERE parent_id=$id AND parent_kind=$kind""".query[Form.Tree.Key]

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
    val set = fr"company=$company, name=$name, description=$description, tree_id=$tree_id, tree_kind=$tree_kind"
    (fr"UPDATE forms " ++ set ++ fr" WHERE id=$id")
      .update
  }

  def deleteTreeQ(key: Form.Tree.Key) = {
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
  override def create(form: Form.Create): F[Form.Record] = insertQ(form.company, form.name, form.description, None)
    .withUniqueGeneratedKeys[Form.Id]("id")
    .map(form.withId _)
    .transact(xa)

  override def update(form: Form.Record): OptionT[F, Form.Record] = OptionT {
    updateQ(form.id, form.data.company, form.data.name, form.data.description, form.data.tree).run
      .flatMap(_ => byIdQ(form.id).option)
      .transact(xa)
  }

  override def get(id: Form.Id): OptionT[F, Form.Record] = OptionT(byIdQ(id).option).transact(xa)

  override def getWithTree(id: Form.Id): OptionT[F, Form.Full] = OptionT {
    for {
      form <- byIdQ(id).option
      tree <- form.traverse(_.data.tree.traverse(getTree.tupled))
    } yield form.map(_.mapData(_.copy(tree = tree.flatten)))
  }.transact(xa)

  val getTree: (Form.Tree.Id, Form.Tree.Kind) => ConnectionIO[Form.Tree] = (rootId: Form.Tree.Id, rootKind: Form.Tree.Kind) => {
    for {
      node <- getTreeNodeByIdQ(rootId, rootKind).unique
      tree <- node match {
        case group: Form.Tree.Group => for {
          childHeaders <- getNodeHeadersByParentIdQ(rootId, rootKind).to[List]
          children <- childHeaders.traverse(getTree.tupled)
        } yield group.copy(children = children)
        case leaf => leaf.pure[ConnectionIO]
      }
    } yield tree
  }

  override def delete(id: Form.Id): OptionT[F, Form.Record] = OptionT {
    byIdQ(id).option.flatMap {
      _.traverse { form => {
        for {
          _ <- deleteQ(form.id).run
          _ <- form.data.tree.traverse(t => deleteTreeQ(t).run)
        } yield form
      }}
    }
  }.transact(xa)

  override def list(pageSize: Int, offset: Int): F[List[Form.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].transact(xa)

  def updateGroup(g: Form.Tree.GroupWithKey, parent: Option[Form.Tree.Key]): F[Form.Tree.GroupWithKey] = {
    val updateHead = updateTreeHeaderQ(g.key, parent).run
    val updateDetails = updateTreeGroupQ(g.id).run
    (updateHead *> updateDetails).transact(xa).as(g)
  }

  def insertGroup(g: Form.Tree.Group, parent: Option[Form.Tree.Key]): F[Form.Tree.GroupWithKey] = {
    insertTreeHeaderQ(g.kind, parent)
      .withUniqueGeneratedKeys[Form.Tree.Id]("id")
      .flatMap(id => insertTreeGroupQ(id).run.as(g.withKey(id)))
      .transact(xa)
  }

  def insertText(t: Form.Tree.Text, parent: Option[Form.Tree.Key]): F[Form.Tree.TextWithKey] = {
    insertTreeHeaderQ(Form.Tree.Kind.Text, parent)
      .withUniqueGeneratedKeys[Form.Tree.Id]("id")
      .flatMap(id => insertTreeTextQ(id, t.text).run.as(t.withKey(id)))
      .transact(xa)
  }

  def updateText(t: Form.Tree.TextWithKey, parent: Option[Form.Tree.Key]): F[Form.Tree.TextWithKey] = {
    val updateHead = updateTreeHeaderQ(t.key, parent).run
    val updateDetails = updateTreeTextQ(t.id, t.text).run
    (updateHead *> updateDetails).transact(xa).as(t)
  }

  def insertQuestion(q: Form.Tree.Question, parent: Option[Form.Tree.Key]): F[Form.Tree.QuestionWithKey] = {
    insertTreeHeaderQ(q.kind, parent)
      .withUniqueGeneratedKeys[Form.Tree.Id]("id")
      .flatMap(id => insertTreeQuestionQ(id, q.label, q.text).run.as(q.withKey(id)))
      .transact(xa)
  }

  def updateQuestion(q: Form.Tree.QuestionWithKey, parent: Option[Form.Tree.Key]): F[Form.Tree.QuestionWithKey] = {
    val updateHead = updateTreeHeaderQ(q.key, parent).run
    val updateDetails = updateTreeQuestionQ(q.id, q.label, q.text).run
    (updateHead *> updateDetails).transact(xa).as(q)
  }

  override def createOrUpdateTree(tree: Form.Tree, parent: Option[Form.Tree.Key]): F[Form.Tree] = {
    import Form.Tree._
    tree match {
      case g: GroupWithKey =>
        updateGroup(g, parent).flatMap { updatedG =>
          g.children
            .traverse(c => createOrUpdateTree(c, updatedG.key.some))
            .as(updatedG)
        }.widen
      case g: Group =>
        insertGroup(g, parent).flatMap { updatedG =>
          g.children
            .traverse(createOrUpdateTree(_, updatedG.key.some))
            .as(updatedG)
        }.widen
      case t: TextWithKey => updateText(t, parent).widen
      case t: Text => insertText(t, parent).widen
      case q: QuestionWithKey => updateQuestion(q, parent).widen
      case q: Question => insertQuestion(q, parent).widen
    }
  }

  override def getTree(key: Form.Tree.Key): OptionT[F, Form.Tree] = ???

  override def deleteTree(key: Form.Tree.Key): OptionT[F, Form.Tree.Record] = {
    OptionT(getTreeNodeByIdQ(key).option)
      .semiflatMap(node => deleteTreeQ(key).run.as(node))
      .transact(xa)
  }

  override def listByCompany(company: Company.Id, pageSize: Int, offset: Int): F[List[Record]] = ???
}
