package com.lion.rafiki.domain

import cats.data.{EitherT, OptionT}
import cats.{Applicative, Monad}
import cats.syntax.all._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import shapeless.tag
import shapeless.tag.@@
import tsec.passwordhashers.PasswordHasher
import tsec.passwordhashers.jca.BCrypt

final case class Company[User](name: String, rh_user: User) {
  def withId(id: Company.Id) = WithId(id, this)
}

object Company extends TaggedId[Company[_]] {

  implicit def companyDecoder[T: Decoder]: Decoder[Company[T]] = deriveDecoder
  implicit def companyEncoder[T: Encoder]: Encoder[Company[T]] = deriveEncoder
  implicit val companyCreateDecoder: Decoder[Company.Create] = deriveDecoder
  implicit val companyUpdateDecoder: Decoder[Company.Update] = WithId.decoder
  implicit val companyWithIdFullEncoder: Encoder[Company.Full] = WithId.encoder

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
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listWithUser(pageSize: Int, offset: Int): Result[List[Full]]
  }

  class Service[F[_]: Monad](companyRepo: Repo[F], userService: User.Service[F])(implicit P: PasswordHasher[F, BCrypt]) {
    type Result[T] = EitherT[F, ValidationError, T]
    def create(company: Create): Result[Full] =
      for {
        createdUser <- userService.create(company.rh_user)
        saved <- companyRepo.create(company.copy(rh_user = createdUser.id)).leftMap[ValidationError](ValidationError.Repo)
      } yield saved.mapData(_.copy(rh_user = createdUser.data))

    def update(company: Update): Result[Full] =
      for {
        companyUserId <- companyRepo.get(company.id).map(_.data.rh_user).leftMap[ValidationError](ValidationError.Repo)
        updatedUser <- userService.update(company.data.rh_user.withId(companyUserId))
        fullCompany = company.mapData(_.copy(rh_user = updatedUser.id))
        saved <- companyRepo.update(fullCompany).leftMap[ValidationError](ValidationError.Repo)
      } yield saved.mapData(_.copy(rh_user = updatedUser.data))

    def get(id: Id): Result[Full] =
      for {
        company <- companyRepo.get(id).leftMap[ValidationError](ValidationError.Repo)
        user <- userService.getById(company.data.rh_user)
      } yield company.mapData(_.copy(rh_user = user.data))

    def getFromUser(id: User.Id): Result[Full] =
      for {
        user <- userService.getById(id)
        company <-  companyRepo.getByUser(id).leftMap[ValidationError](ValidationError.Repo)
      } yield company.mapData(_.copy(rh_user = user.data))

    def delete(id: Id): Result[Unit] = companyRepo.delete(id).leftMap[ValidationError](ValidationError.Repo)

    def list(pageSize: Int, offset: Int): Result[List[Record]] = companyRepo.list(pageSize, offset).leftMap[ValidationError](ValidationError.Repo)

    def listWithUser(pageSize: Int, offset: Int): Result[List[Full]] = companyRepo.listWithUser(pageSize, offset).leftMap[ValidationError](ValidationError.Repo)
  }
}
