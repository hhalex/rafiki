package com.lion.rafiki.sql

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxOptionId
import com.lion.rafiki.Conf
import com.lion.rafiki.domain.Company
import com.lion.rafiki.domain.company.Form
import doobie.util.transactor.Transactor
import org.specs2.mutable.Specification
import doobie.specs2._
import doobie.implicits._

object FormSQLSpec extends Specification with IOChecker {

  val conf = Conf[IO]().unsafeRunSync()

  val transactor = {
    val xa = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      conf.dbUrl,
      conf.dbUser,
      conf.dbPassword
    )
    create.allTables.transact(xa).unsafeRunSync()
    xa
  }

  import FormSQL._

  val formId = Form.tag(2)
  val treeId = Form.Tree.tag(2)
  val treeKey = (treeId, Form.Tree.Kind.Group)
  val companyId = Company.tag(2)

  check(byIdQ(formId))
  check(getTreeQuestionQ(treeId))
  check(getTreeTextQ(treeId))
  check(getTreeGroupQ(treeId))
  check(getTreeChildKeysByIdQ(treeKey))
  check(byCompanyIdQ(companyId))
  check(insertQ(companyId.some, "name", "description".some, treeKey.some))
  check(insertTreeHeaderQ(Form.Tree.Kind.Group, treeKey.some))
  check(insertTreeQuestionQ(treeId, "label", "text"))
  check(insertTreeGroupQ(treeId))
  check(insertTreeTextQ(treeId, "text"))
  check(
    updateQ(formId, companyId.some, "name", "description".some, treeKey.some)
  )
  check(deleteTreeQ(treeKey))
  check(deleteTreeChildQ(treeKey, treeKey :: Nil))
  check(updateTreeHeaderQ(treeKey, treeKey.some))
  check(updateTreeQuestionQ(treeId, "label", "text"))
  check(updateTreeTextQ(treeId, "text"))
  check(updateTreeGroupQ(treeId))
  check(deleteQ(formId))
  check(listAllQ(10, 10))

}
