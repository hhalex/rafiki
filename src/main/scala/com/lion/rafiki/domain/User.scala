package com.lion.rafiki.domain

import cats.data.{EitherT, OptionT}
import cats.{Applicative, Monad}
import cats.syntax.all._
import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps
import shapeless.tag
import shapeless.tag.@@
import tsec.passwordhashers.{PasswordHash, PasswordHasher}
import tsec.passwordhashers.jca.BCrypt

final case class User[Password](
                            firstname: Option[String],
                            name: Option[String],
                            username: String,
                            password: Password
                          ) {
  def withId(id: User.Id) = WithId(id, this)
}

object User {
  type Id = Long @@ User[_]
  val tagSerial = tag[User[_]](_: Long)

  implicit val userIdDecoder: Decoder[Id] = Decoder[Long].map(tagSerial)
  implicit val userIdEncoder: Encoder[Id] = Encoder[Long].contramap(_.asInstanceOf[Long])
  implicit val userPassStrEncoder: Encoder[User[PasswordHash[BCrypt]]] = Encoder.instance {
    u => Json.obj("username" -> u.username.asJson)
  }
  implicit val userCreateDecoder: Decoder[User.Create] = deriveDecoder
  implicit val userUpdateDecoder: Decoder[User.Update] = WithId.decoder
  implicit val userFullEncoder: Encoder[User.Full] = WithId.encoder

  case class Authed(id: Id, username: String, password: PasswordHash[BCrypt])

  type CreateRecord = User[PasswordHash[BCrypt]]
  type Record = WithId[Id, CreateRecord]
  type Create = User[String]
  type Update = WithId[Id, Create]

  type withId[T] = WithId[Id, User[T]]

  type Full = Record

  trait Repo[F[_]] {
    def create(user: CreateRecord): F[Record]
    def update(user: WithId[Id, User[Option[PasswordHash[BCrypt]]]]): OptionT[F, Record]
    def get(id: Id): OptionT[F, Record]
    def delete(id: Id): OptionT[F, Record]
    def list(pageSize: Int, offset: Int): F[List[Record]]
    def findByUserName(userName: String): OptionT[F, Record]
    def deleteByUserName(userName: String): OptionT[F, Record]
  }

  trait Validation[F[_]] {
    def doesNotExist(c: Create): EitherT[F, ValidationError, Unit]
    def exists(c: Id): EitherT[F, ValidationError, Unit]
  }

  class FromRepoValidation[F[_] : Applicative](repo: Repo[F]) extends Validation[F] {
    override def doesNotExist(user: Create): EitherT[F, ValidationError, Unit] =
      repo
        .findByUserName(user.username)
        .map(ValidationError.UserAlreadyExists(_): ValidationError)
        .toLeft(())

    override def exists(userId: Id): EitherT[F, ValidationError, Unit] =
      repo
        .get(userId)
        .toRight(ValidationError.UserNotFound: ValidationError)
        .void
  }

  class Service[F[_]: Monad](repo: Repo[F], validation: Validation[F])(implicit P: PasswordHasher[F, BCrypt]) {
    def create(user: Create): EitherT[F, ValidationError, Full] =
      for {
        _ <- validation.doesNotExist(user)
        hashedPassword <- EitherT.liftF(BCrypt.hashpw[F](user.password))
        saved <- EitherT.liftF[F, ValidationError, Full](repo.create(user.copy(password = hashedPassword)))
      } yield saved

    def getById(userId: Id): EitherT[F, ValidationError, Full] =
      repo.get(userId).toRight(ValidationError.UserNotFound: ValidationError)

    def getByName(userName: String): EitherT[F, ValidationError, Full] =
      repo.findByUserName(userName).toRight(ValidationError.UserNotFound: ValidationError)

    def delete(userId: Id): F[Unit] =
      repo.delete(userId).value.void

    def deleteByUserName(userName: String): F[Unit] =
      repo.deleteByUserName(userName).value.void

    def update(user: Update): EitherT[F, ValidationError, Full] =
      for {
        _ <- validation.exists(user.id)
        password <- EitherT.liftF(if (user.data.password == "") None.pure[F] else BCrypt.hashpw[F](user.data.password).map(Some(_)))
        saved <- repo.update(user.mapData(_.copy(password = password))).toRight(ValidationError.UserNotFound: ValidationError)
      } yield saved

    def list(pageSize: Int, offset: Int): F[List[Full]] =
      repo.list(pageSize, offset)
  }
}
