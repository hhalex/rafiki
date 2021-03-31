package com.lion.rafiki.domain

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.implicits.toFunctorOps
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
  implicit val companyContractKindDecoder: Decoder[Kind] = Decoder[String].emap(Kind.fromStringE)
  implicit val companyContractKindEncoder: Encoder[Kind] = Encoder[String].contramap(_.toString)
  implicit val companyContractCreateDecoder: Decoder[CreateRecord] = deriveDecoder
  implicit val companyContractCreateEncoder: Encoder[CreateRecord] = deriveEncoder
  implicit val companyContractWithIdDecoder: Decoder[Record] = WithId.decoder
  implicit val companyContractWithIdEncoder: Encoder[Record] = WithId.encoder

  type CreateRecord = CompanyContract[Company.Id]
  type Record = WithId[Id, CreateRecord]

  trait Repo[F[_]] {
    def create(c: CreateRecord): F[Record]
    def update(c: Record): OptionT[F, Record]
    def get(id: Id): OptionT[F, Record]
    def delete(id: Id): OptionT[F, Record]
    def list(pageSize: Int, offset: Int): F[List[Record]]
    def getByCompany(companyId: Company.Id): F[List[Record]]
  }

  class Service[F[_]: Monad](companyContractRepo: Repo[F], companyValidation: Company.Validation[F])(implicit P: PasswordHasher[F, BCrypt]) {
    def create(companyContract: CreateRecord): EitherT[F, ValidationError, Record] =
      for {
        _ <- companyValidation.exists(companyContract.company)
        saved <- EitherT.liftF(companyContractRepo.create(companyContract))
      } yield saved

    def update(companyContract: Record): EitherT[F, ValidationError, Record] =
      EitherT.fromOptionF(
        companyContractRepo.update(companyContract).value,
        ValidationError.CompanyContractNotFound: ValidationError
      )

    def get(id: Id): EitherT[F, ValidationError, Record] =
      EitherT.fromOptionF(companyContractRepo.get(id).value, ValidationError.CompanyContractNotFound: ValidationError)

    def delete(id: Id): F[Unit] = companyContractRepo.delete(id).value.as(())

    def list(pageSize: Int, offset: Int): F[List[Record]] = companyContractRepo.list(pageSize, offset)
  }
}


