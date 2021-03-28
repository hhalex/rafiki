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

object Company {
  type Id = Long @@ Company[_]
  val tagSerial = tag[Company[_]](_: Long)

  implicit val companyIdDecoder: Decoder[Id] = Decoder[Long].map(Company.tagSerial)
  implicit val companyIdEncoder: Encoder[Id] = Encoder[Long].contramap(_.asInstanceOf[Long])
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
    def create(c: CreateRecord): F[Record]
    def update(c: Record): OptionT[F, Record]
    def get(id: Id): OptionT[F, Record]
    def getByUser(id: User.Id): OptionT[F, Record]
    def delete(id: Id): OptionT[F, Record]
    def list(pageSize: Int, offset: Int): F[List[Record]]
    def listWithUser(pageSize: Int, offset: Int): F[List[Full]]
  }

  trait Validation[F[_]] {
    def doesNotExist(c: Company[_]): EitherT[F, ValidationError, Unit]
    def exists(c: Id): EitherT[F, ValidationError, Unit]
  }

  class FromRepoValidation[F[_]](repo: Repo[F])(implicit A: Applicative[F]) extends Validation[F] {
    override def doesNotExist(c: Company[_]): EitherT[F, ValidationError, Unit] =
      EitherT.fromEither[F](Right[ValidationError.CompanyAlreadyExists, Unit](()))

    override def exists(companyId: Id): EitherT[F, ValidationError, Unit] =
      repo
        .get(companyId)
        .toRight(ValidationError.CompanyNotFound: ValidationError)
        .void
  }

  class Service[F[_]: Monad](companyRepo: Repo[F], validation: Validation[F], userService: User.Service[F])(implicit P: PasswordHasher[F, BCrypt]) {
    def create(company: Create): EitherT[F, ValidationError, Full] =
      for {
        _ <- validation.doesNotExist(company)
        createdUser <- userService.create(company.rh_user)
        saved <- EitherT.liftF(companyRepo.create(company.copy(rh_user = createdUser.id)))
      } yield saved.mapData(_.copy(rh_user = createdUser.data))

    def update(company: Update): EitherT[F, ValidationError, Full] =
      for {
        companyUserId <- companyRepo.get(company.id).map(_.data.rh_user).toRight(ValidationError.CompanyNotFound: ValidationError)
        updatedUser <- userService.update(company.data.rh_user.withId(companyUserId))
        fullCompany = company.mapData(_.copy(rh_user = updatedUser.id))
        saved <- EitherT.fromOptionF(companyRepo.update(fullCompany).value, ValidationError.CompanyNotFound: ValidationError)
      } yield saved.mapData(_.copy(rh_user = updatedUser.data))

    def get(id: Id): EitherT[F, ValidationError, Full] =
      for {
        company <- EitherT.fromOptionF(companyRepo.get(id).value, ValidationError.CompanyNotFound: ValidationError)
        user <- userService.getById(company.data.rh_user)
      } yield company.mapData(_.copy(rh_user = user.data))

    def getFromUser(id: User.Id): EitherT[F, ValidationError, Full] =
      for {
        user <- userService.getById(id)
        company <-  EitherT.fromOptionF(companyRepo.getByUser(id).value, ValidationError.CompanyNotFound: ValidationError)
      } yield company.mapData(_.copy(rh_user = user.data))

    def delete(id: Id): F[Unit] = companyRepo.delete(id).value.as(())

    def list(pageSize: Int, offset: Int): F[List[Record]] = companyRepo.list(pageSize, offset)

    def listWithUser(pageSize: Int, offset: Int): F[List[Full]] = companyRepo.listWithUser(pageSize, offset)
  }
}
