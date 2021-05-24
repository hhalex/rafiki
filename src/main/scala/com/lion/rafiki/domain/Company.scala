package com.lion.rafiki.domain

import cats.data.EitherT
import cats.Monad
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.all._
import com.lion.rafiki.auth.PasswordError

final case class Company[User](name: String, rh_user: User):
  def withId(id: Company.Id) = WithId(id, this)

trait CompanyId
object Company extends TaggedId[CompanyId] {

  import Id.given
  import User.Id.given
  given [T: Decoder]: Decoder[Company[T]] = deriveDecoder
  given [T: Encoder]: Encoder[Company[T]] = deriveEncoder
  given Decoder[Create] = deriveDecoder
  given Decoder[CreateRecord] = deriveDecoder
  given Encoder[Update] = deriveEncoder
  given Encoder[Full] = deriveEncoder

  type CreateRecord = Company[User.Id]
  type Record = WithId[Id, CreateRecord]
  type Create = Company[User.Create]
  type Update = WithId[Id, Create]
  type Full = WithId[Id, Company[User.CreateRecord]]

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(c: CreateRecord): Result[Record]
    def update(c: Record): Result[Record]
    def get(id: Id): Result[Record]
    def getByUser(id: User.Id): Result[Record]
    def getByUserEmail(userEmail: String): Result[Record]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listWithUser(pageSize: Int, offset: Int): Result[List[Full]]
  }

  class Service[F[_]: Monad](companyRepo: Repo[F], userService: User.Service[F]) {
    type Result[T] = EitherT[F, ValidationError | RepoError, T]
    def create(company: Create):  EitherT[F, ValidationError | RepoError | PasswordError, Full] =
      for
        createdUser <- userService.create(company.rh_user)
        saved <- companyRepo.create(company.copy(rh_user = createdUser.id)).leftWiden
      yield saved.mapData(_.copy(rh_user = createdUser.data))

    def update(company: Update): Result[Full] =
      for
        companyUserId <- companyRepo.get(company.id).map(_.data.rh_user).leftWiden
        updatedUser <- userService.update(company.data.rh_user.withId(companyUserId))
        fullCompany = company.mapData(_.copy(rh_user = updatedUser.id))
        saved <- companyRepo.update(fullCompany).leftWiden
      yield saved.mapData(_.copy(rh_user = updatedUser.data))

    def get(id: Id): Result[Full] =
      for
        company <- companyRepo.get(id).leftWiden
        user <- userService.getById(company.data.rh_user)
      yield company.mapData(_.copy(rh_user = user.data))

    def delete(id: Id): Result[Unit] = companyRepo.delete(id).leftWiden

    def list(pageSize: Int, offset: Int): Result[List[Record]] = companyRepo.list(pageSize, offset).leftWiden

    def listWithUser(pageSize: Int, offset: Int): Result[List[Full]] = companyRepo.listWithUser(pageSize, offset).leftWiden
  }
}
