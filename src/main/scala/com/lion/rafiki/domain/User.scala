package com.lion.rafiki.domain

import cats.data.EitherT
import cats.Monad
import cats.Functor
import cats.syntax.all._
import com.lion.rafiki.auth.PasswordHasher
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps
import com.lion.rafiki.auth.PasswordError

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

  class Service[F[_]: Monad: Functor](repo: Repo[F], passwordHasher: PasswordHasher[F]) {
    type Result[T] = EitherT[F, ValidationError | RepoError, T]
    def create(user: Create): EitherT[F, ValidationError | RepoError | PasswordError, Record] =
      for
        hashedPassword <- passwordHasher.hashPwd(user.password).leftWiden
        saved <- repo.create(user.copy(password = hashedPassword)).leftWiden
      yield saved

    def getById(userId: Id): Result[Full] =
      repo.get(userId).leftWiden

    def getByName(email: String): Result[Full] = repo.findByUserName(email).leftWiden

    def validateCredentials(email: String, password: String): EitherT[F, ValidationError | RepoError | PasswordError, Record] = for
      user <- repo.findByUserName(email).leftWiden
      isValidPassword <- passwordHasher.checkPwd(password, user.data.password).leftWiden[ValidationError | RepoError | PasswordError]
      _ <- (if isValidPassword then EitherT.rightT[F, ValidationError](()) else EitherT.leftT[F, Unit](ValidationError.UserCredentialsIncorrect)).leftWiden
    yield user

    def delete(userId: Id): F[Unit] =
      repo.delete(userId).value.void

    def deleteByUserName(userName: String): Result[Unit] =
      repo.deleteByUserName(userName).leftWiden

    def update(user: Update): Result[Full] =
      for
        password <-  EitherT.liftF(if user.data.password == "" then None.pure[F] else passwordHasher.hashPwd(user.data.password).toOption.value)
        saved <- repo.update(user.mapData(_.copy(password = password))).leftWiden
      yield saved

    def list(pageSize: Int, offset: Int): Result[List[Full]] =
      repo.list(pageSize, offset).leftWiden
  }
}
