package com.lion.rafiki.domain

import cats.Monad
import cats.data.EitherT
import cats.implicits.{toBifunctorOps, toFunctorOps}
import com.lion.rafiki.domain
import com.lion.rafiki.domain.company.Form
import com.lion.rafiki.domain.company.Form.Repo
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import shapeless.tag
import shapeless.tag.@@
import tsec.passwordhashers.PasswordHasher
import tsec.passwordhashers.jca.BCrypt

final case class CompanyContract[Company](company: Company, kind: CompanyContract.Kind) {
  def withId(id: CompanyContract.Id) = WithId(id, this)
}

object CompanyContract {
  sealed trait Kind extends Product with Serializable {
    override def toString: String = this match {
      case Kind.Unlimited => "unlimited"
      case Kind.OneShot => "oneshot"
      case Kind.Unknown(s) => s"Unknown contract: '$s'"
    }
  }
  object Kind {
    case object OneShot extends Kind
    case object Unlimited extends Kind
    case class Unknown(kind: String) extends Kind

    implicit val companyContractKindDecoder: Decoder[Kind] = Decoder[String].emap(Kind.fromStringE)
    implicit val companyContractKindEncoder: Encoder[Kind] = Encoder[String].contramap(_.toString)

    def fromString(s: String) = s.toLowerCase match {
      case "unlimited" => Kind.Unlimited
      case "oneshot" => Kind.OneShot
      case other => Kind.Unknown(other)
    }
    def fromStringE(s: String) = fromString(s) match {
      case Unknown(other) => Left(s"'$other' is not a member value of CompanyContract.Kind")
      case contract => Right(contract)
    }
  }

  type Id = Long @@ CompanyContract[_]
  val tagSerial = tag[CompanyContract[_]](_: Long)

  implicit val companyContractIdDecoder: Decoder[Id] = Decoder[Long].map(CompanyContract.tagSerial)
  implicit val companyContractIdEncoder: Encoder[Id] = Encoder[Long].contramap(_.asInstanceOf[Long])
  implicit val companyContractCreateDecoder: Decoder[CreateRecord] = deriveDecoder
  implicit val companyContractCreateEncoder: Encoder[CreateRecord] = deriveEncoder
  implicit val companyContractWithIdDecoder: Decoder[Record] = WithId.decoder
  implicit val companyContractWithIdEncoder: Encoder[Record] = WithId.encoder

  type CreateRecord = CompanyContract[Company.Id]
  type Record = WithId[Id, CreateRecord]

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(c: CreateRecord): Result[Record]
    def update(c: Record): Result[Record]
    def get(id: Id): Result[Record]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompany(companyId: Company.Id, pageSize: Int, offset: Int): Result[List[Record]]
  }

  trait Validation[F[_]] {
    def hasOwnership(companyContractId: CompanyContract.Id, companyId: Option[Company.Id]): EitherT[F, ValidationError, Record]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F]) extends Validation[F] {
    val notAllowed = EitherT.leftT[F, Unit](ValidationError.NotAllowed).leftWiden[ValidationError]
    override def hasOwnership(companyContractId: domain.CompanyContract.Id, companyId: Option[Company.Id]): EitherT[F, ValidationError, Record] = for {
      repoContract <- repo.get(companyContractId).leftMap(ValidationError.Repo)
      success = EitherT.rightT[F, ValidationError](repoContract)
      _ <- companyId match {
        case Some(id) if id == repoContract.data.company => success
        case Some(_) => notAllowed
        case None => success
      }
    } yield repoContract
  }

  class Service[F[_]: Monad](companyContractRepo: Repo[F])(implicit P: PasswordHasher[F, BCrypt]) {
    type Result[T] = EitherT[F, ValidationError, T]
    def create(companyContract: CreateRecord): Result[Record] =
      companyContractRepo.create(companyContract).leftMap[ValidationError](ValidationError.Repo)

    def update(companyContract: Record): Result[Record] =
      companyContractRepo.update(companyContract).leftMap[ValidationError](ValidationError.Repo)

    def get(id: Id): Result[Record] =
      companyContractRepo.get(id).leftMap[ValidationError](ValidationError.Repo)

    def delete(id: Id): Result[Unit] =
      companyContractRepo.delete(id).as(()).leftMap[ValidationError](ValidationError.Repo)

    def list(pageSize: Int, offset: Int): Result[List[Record]] =
      companyContractRepo.list(pageSize, offset).leftMap[ValidationError](ValidationError.Repo)

    def listByCompany(companyId: Company.Id, pageSize: Int, offset: Int): Result[List[Record]] =
      companyContractRepo.listByCompany(companyId, pageSize, offset).leftMap[ValidationError](ValidationError.Repo)
  }
}


