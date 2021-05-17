package com.lion.rafiki.domain

import cats.data.EitherT
import cats.Monad
import cats.syntax.all._
import com.lion.rafiki.auth.PasswordHasher
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps

final case class User[Password](
                            firstname: Option[String],
                            name: Option[String],
                            username: String,
                            password: Password
                          ) {
  def withId(id: User.Id) = WithId(id, this)
}

trait UserId
object User extends TaggedId[UserId] {

  import User.Id.given
  given [T]: Encoder[User[T]] = Encoder.instance {
    u => Json.obj("username" -> u.username.asJson)
  }
  given Decoder[Create] = deriveDecoder
  given Decoder[Update] = deriveDecoder
  given Encoder[Full] = deriveEncoder

  case class Authed(email: String)

  type CreateRecord = User[PasswordHasher.Password]
  type Record = WithId[Id, CreateRecord]
  type Create = User[String]
  type Update = WithId[Id, Create]

  type withId[T] = WithId[Id, User[T]]

  type Full = Record

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(user: CreateRecord): Result[Record]
    def update(user: WithId[Id, User[Option[PasswordHasher.Password]]]): Result[Record]
    def get(id: Id): Result[Record]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def findByUserName(userName: String): Result[Record]
    def deleteByUserName(userName: String): Result[Unit]
  }

  class Service[F[_]: Monad](repo: Repo[F], passwordHasher: PasswordHasher[F]) {
    type Result[T] = EitherT[F, ValidationError, T]
    def create(user: Create): Result[Full] =
      for
        hashedPassword <- passwordHasher.hashPwd(user.password).leftMap[ValidationError](ValidationError.Password)
        saved <- repo.create(user.copy(password = hashedPassword)).leftMap[ValidationError](ValidationError.Repo)
      yield saved

    def getById(userId: Id): Result[Full] =
      repo.get(userId).leftMap[ValidationError](ValidationError.Repo)

    def getByName(email: String): Result[Full] = repo.findByUserName(email).leftMap[ValidationError] (ValidationError.Repo)

    def validateCredentials(email: String, password: String): Result[Full] = for
      user <- repo.findByUserName(email).leftMap[ValidationError] (ValidationError.Repo)
      isValidPassword <- passwordHasher.checkPwd(password, user.data.password).leftMap[ValidationError](ValidationError.Password)
      _ <- if isValidPassword then EitherT.rightT[F, ValidationError](()) else EitherT.leftT[F, Unit](ValidationError.UserCredentialsIncorrect: ValidationError)
    yield user

    def delete(userId: Id): F[Unit] =
      repo.delete(userId).value.void

    def deleteByUserName(userName: String): Result[Unit] =
      repo.deleteByUserName(userName).leftMap[ValidationError](ValidationError.Repo)

    def update(user: Update): Result[Full] =
      for
        password <-  EitherT.liftF(if user.data.password == "" then None.pure[F] else passwordHasher.hashPwd(user.data.password).toOption.value)
        saved <- repo.update(user.mapData(_.copy(password = password))).leftMap[ValidationError](ValidationError.Repo)
      yield saved

    def list(pageSize: Int, offset: Int): Result[List[Full]] =
      repo.list(pageSize, offset).leftMap[ValidationError](ValidationError.Repo)
  }
}
