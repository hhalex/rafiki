package com.lion.rafiki.domain.company

import cats.data.EitherT
import com.lion.rafiki.domain.{CompanyContract, RepoError, WithId}
import shapeless.tag
import shapeless.tag.@@

import java.time.Instant

case class FormSession(companyContract: CompanyContract.Id, testForm: Form.Id, name: String, startDate: Option[Instant], endDate: Option[Instant]) {
  def withId(id: FormSession.Id) = WithId(id, this)
}

object FormSession {
  type Id = Long @@ FormSession
  val tagSerial = tag[FormSession](_: Long)

  type Create = FormSession
  type Update = WithId[Id, Create]
  type Record = Update
  type Full = Update

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(formSession: Create): Result[Full]
    def update(formSession: Update): Result[Full]
    def get(id: Id): Result[Full]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompanyContract(company: CompanyContract.Id, pageSize: Int, offset: Int): Result[List[Record]]
  }

}


