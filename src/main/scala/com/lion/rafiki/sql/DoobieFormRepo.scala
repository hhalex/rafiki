package com.lion.rafiki.sql

import doobie.implicits._
import doobie._
import cats.implicits._
import cats.syntax.all._
import cats.effect.MonadCancel
import cats.implicits.toFunctorOps
import com.lion.rafiki.domain.company.{Form, QuestionAnswer, QuestionAnswerP, FormTree}
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

private[sql] object FormSQL {
  import CompanySQL._
  import FormTreeSQL.{formTreeIdMeta, formTreeKindMeta, getTreeRecCIO, syncTree, deleteTreeQ}
  implicit val formIdMeta: Meta[Form.Id] = createMetaId(Form)

  implicit val formRecordReader: Read[Form.Record] = Read[
    (
        Form.Id,
        Option[Company.Id],
        String,
        Option[String],
        Option[FormTree.Id],
        Option[FormTree.Kind]
    )
  ]
    .map({ case (id, company, name, description, tree_id, tree_kind) =>
      Form(company, name, description, tree_id.zip(tree_kind)).withId(id)
    })


  implicit val han: LogHandler = LogHandler.jdkLogHandler

  def byIdQ(id: Form.Id) =
    sql"""SELECT * FROM forms WHERE id=$id""".query[Form.Record]

  def byCompanyIdQ(id: Company.Id) =
    sql"""SELECT * FROM forms WHERE company=$id OR company=NULL"""
      .query[Form.Record]

  def insertQ(
      company: Option[Company.Id],
      name: String,
      description: Option[String],
      tree: Option[FormTree.Key]
  ) = {
    val (tree_id, tree_kind) = tree.unzip
    sql"""INSERT INTO forms (company,name,description,tree_id,tree_kind) VALUES ($company,$name,$description,$tree_id,$tree_kind)""".update
  }

  def updateQ(
      id: Form.Id,
      company: Option[Company.Id],
      name: String,
      description: Option[String],
      tree: Option[FormTree.Key]
  ) = {
    val (tree_id, tree_kind) = tree.unzip
    sql"""UPDATE forms SET company=$company, name=$name, description=$description, tree_id=$tree_id, tree_kind=$tree_kind WHERE id=$id""".update
  }

  def deleteQ(id: Form.Id) =
    sql"DELETE FROM forms WHERE id=$id".update

  def listAllQ(pageSize: Int, offset: Int) =
    paginate(pageSize, offset)(
      sql"SELECT * FROM forms".query[Form.Record]
    )

  def getCIO(id: Form.Id): ConnectionIO[Form.Full] = for {
    form <- byIdQ(id).unique
    tree <- form.data.tree.traverse(getTreeRecCIO)
  } yield form.mapData(_.copy(tree = tree))
}

class DoobieFormRepo[F[_]: TaglessMonadCancel](val xa: Transactor[F])
    extends Form.Repo[F] {
  import FormSQL._
  import FormTreeSQL.{deleteTreeQ, syncTree}
  import RepoError.ConnectionIOwithErrors

  override def create(form: Form.Create): Result[Form.Full] = (for {
    createdTreeKey <- form.tree.traverse(t => syncTree(t, None))
    id <- insertQ(form.company, form.name, form.description, createdTreeKey)
      .withUniqueGeneratedKeys[Form.Id]("id")
    createdForm <- getCIO(id)
  } yield createdForm).toResult().transact(xa)

  override def update(form: Form.Update): Result[Form.Full] = (for {
    updateTreeKey <- form.data.tree.traverse(t => syncTree(t, None))
    _ <- updateQ(
      form.id,
      form.data.company,
      form.data.name,
      form.data.description,
      updateTreeKey
    ).run
    updatedForm <- getCIO(form.id)
  } yield updatedForm).toResult().transact(xa)

  override def get(id: Form.Id): Result[Form.Full] =
    getCIO(id).toResult().transact(xa)

  override def delete(id: Form.Id): Result[Unit] = (for {
    form <- byIdQ(id).unique
    _ <- deleteQ(form.id).run
    _ <- form.data.tree.traverse(t => deleteTreeQ(t).run)
  } yield ()).toResult().transact(xa)

  override def list(pageSize: Int, offset: Int): Result[List[Form.Record]] =
    listAllQ(pageSize: Int, offset: Int).to[List].toResult().transact(xa)

  override def listByCompany(
      company: Company.Id,
      pageSize: Int,
      offset: Int
  ): Result[List[Form.Record]] =
    byCompanyIdQ(company).to[List].toResult().transact(xa)
}
