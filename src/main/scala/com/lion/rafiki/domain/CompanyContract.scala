package com.lion.rafiki.domain

import cats.Monad
import cats.data.EitherT
import cats.syntax.all._
import cats.implicits.{toBifunctorOps, toFunctorOps}
import com.lion.rafiki.domain
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class CompanyContract[Company](
    company: Company,
    kind: CompanyContract.Kind
) {
  def withId(id: CompanyContract.Id) = WithId(id, this)
}

trait ContractId
object CompanyContract extends TaggedId[ContractId] {

  import Company.Id.given
  import Id.given
  enum Kind(val str: String):
    case Oneshot extends Kind("oneshot")
    case UnlimitedOpen extends Kind("unlimited_open")
    case UnlimitedClosed extends Kind("unlimited_closed")

  object Kind:
    given Decoder[Kind] = Decoder.decodeString.emap(fromStringE)
    given Encoder[Kind] = Encoder[String].contramap(_.toString)
    def fromStringE(s: String) = s match
      case Kind.Oneshot.str => Kind.Oneshot.asRight
      case Kind.UnlimitedOpen.str => Kind.UnlimitedOpen.asRight
      case Kind.UnlimitedClosed.str => Kind.UnlimitedClosed.asRight
      case contract => Left(s"'$contract' is not a member value of CompanyContract.Kind")

  given [T: Encoder]: Encoder[CompanyContract[T]] = deriveEncoder
  given [T: Decoder]: Decoder[CompanyContract[T]] = deriveDecoder

  given Encoder[Record] = deriveEncoder
  given Decoder[CreateRecord] = deriveDecoder
  type CreateRecord = CompanyContract[Company.Id]
  type Record = WithId[Id, CreateRecord]

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(c: CreateRecord): Result[Record]
    def update(c: Record): Result[Record]
    def get(id: Id): Result[Record]
    def getByCompany(companyId: Company.Id): Result[List[Record]]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompany(
        companyId: Company.Id,
        pageSize: Int,
        offset: Int
    ): Result[List[Record]]
  }

  trait Validation[F[_]] {
    def hasOwnership(
        companyContractId: CompanyContract.Id,
        companyId: Option[Company.Id]
    ): EitherT[F, ValidationError | RepoError, Record]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F]) extends Validation[F] {
    val notAllowed = EitherT
      .leftT[F, Unit](ValidationError.NotAllowed)
      .leftWiden[ValidationError | RepoError]
    override def hasOwnership(
        companyContractId: domain.CompanyContract.Id,
        companyId: Option[Company.Id]
    ): EitherT[F, ValidationError | RepoError, Record] = for
      repoContract <- repo.get(companyContractId).leftWiden
      success = EitherT.rightT[F, ValidationError | RepoError](repoContract)
      _ <- companyId match {
        case Some(id) if id == repoContract.data.company => success
        case Some(_)                                     => notAllowed
        case None                                        => success
      }
    yield repoContract
  }

  class Service[F[_]: Monad](companyContractRepo: Repo[F]) {
    type Result[T] = EitherT[F, ValidationError | RepoError, T]
    def create(companyContract: CreateRecord): Result[Record] =
      companyContractRepo
        .create(companyContract)
        .leftWiden

    def update(companyContract: Record): Result[Record] =
      companyContractRepo
        .update(companyContract)
        .leftWiden

    def get(id: Id): Result[Record] =
      companyContractRepo.get(id).leftWiden

    def delete(id: Id): Result[Unit] =
      companyContractRepo
        .delete(id)
        .as(())
        .leftWiden

    def list(pageSize: Int, offset: Int): Result[List[Record]] =
      companyContractRepo
        .list(pageSize, offset)
        .leftWiden

    def listByCompany(
        companyId: Company.Id,
        pageSize: Int,
        offset: Int
    ): Result[List[Record]] =
      companyContractRepo
        .listByCompany(companyId, pageSize, offset)
        .leftWiden
  }
}
