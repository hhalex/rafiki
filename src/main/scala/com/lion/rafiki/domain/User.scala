package com.lion.rafiki.domain

import cats.conversions.all.autoWidenBifunctor
import cats.data.{EitherT, OptionT}
import cats.{Applicative, Monad}
import cats.syntax.all._
import com.lion.rafiki.auth.Role
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

object User extends TaggedId[User[_]] {

  implicit def userPassStrEncoder[T]: Encoder[User[T]] = Encoder.instance {
    u => Json.obj("username" -> u.username.asJson)
  }
  implicit val userCreateDecoder: Decoder[User.Create] = deriveDecoder
  implicit val userUpdateDecoder: Decoder[User.Update] = WithId.decoder
  implicit val userFullEncoder: Encoder[User.Full] = WithId.encoder

  case class Authed(id: Id, username: String, password: PasswordHash[BCrypt], role: Role)

  type CreateRecord = User[PasswordHash[BCrypt]]
  type Record = WithId[Id, CreateRecord]
  type Create = User[String]
  type Update = WithId[Id, Create]

  type withId[T] = WithId[Id, User[T]]

  type Full = Record

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(user: CreateRecord): Result[Record]
    def update(user: WithId[Id, User[Option[PasswordHash[BCrypt]]]]): Result[Record]
    def get(id: Id): Result[Record]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def findByUserName(userName: String): Result[Record]
    def deleteByUserName(userName: String): Result[Unit]
  }

  class Service[F[_]: Monad](repo: Repo[F])(implicit P: PasswordHasher[F, BCrypt]) {
    type Result[T] = EitherT[F, ValidationError, T]
    def create(user: Create): Result[Full] =
      for {
        hashedPassword <- EitherT.liftF(BCrypt.hashpw[F](user.password))
        saved <- repo.create(user.copy(password = hashedPassword)).leftMap[ValidationError](ValidationError.Repo)
      } yield saved

    def getById(userId: Id): Result[Full] =
      repo.get(userId).leftMap[ValidationError](ValidationError.Repo)

    def getByName(userName: String): Result[Full] =
      repo.findByUserName(userName).leftMap[ValidationError](ValidationError.Repo)

    def delete(userId: Id): F[Unit] =
      repo.delete(userId).value.void

    def deleteByUserName(userName: String): Result[Unit] =
      repo.deleteByUserName(userName).leftMap[ValidationError](ValidationError.Repo)

    def update(user: Update): Result[Full] =
      for {
        password <- EitherT.liftF(if (user.data.password == "") None.pure[F] else BCrypt.hashpw[F](user.data.password).map(Some(_)))
        saved <- repo.update(user.mapData(_.copy(password = password))).leftMap[ValidationError](ValidationError.Repo)
      } yield saved

    def list(pageSize: Int, offset: Int): Result[List[Full]] =
      repo.list(pageSize, offset).leftMap[ValidationError](ValidationError.Repo)
  }
}
