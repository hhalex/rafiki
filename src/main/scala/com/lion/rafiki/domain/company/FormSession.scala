package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import com.lion.rafiki.domain.CompanyContract.Kind
import com.lion.rafiki.domain.{Company, CompanyContract, RepoError, ValidationError, WithId}
import shapeless.tag
import shapeless.tag.@@

import java.time.Instant

case class FormSession(companyContractId: CompanyContract.Id, testForm: Form.Id, name: String, startDate: Option[Instant], endDate: Option[Instant]) {
  def withId(id: FormSession.Id) = WithId(id, this)
}

object FormSession {
  type Id = Long @@ FormSession
  val tagSerial = tag[FormSession](_: Long)

  type Create = FormSession
  type Update = WithId[Id, Create]
  type Record = Update
  type Full = Update

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(formSession: Create): Result[Full]
    def update(formSession: Update): Result[Full]
    def get(id: Id): Result[Full]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompanyContract(companyContractId: CompanyContract.Id, pageSize: Int, offset: Int): Result[List[Record]]
  }

  trait Validation[F[_]] {
    def canCreateSession(id: CompanyContract.Id): EitherT[F, ValidationError, Unit]
    def hasOwnership(id: FormSession.Id, companyId: Option[Company.Id]): EitherT[F, ValidationError, Unit]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F], companyContractRepo: CompanyContract.Repo[F], formValidation: Form.Validation[F]) extends Validation[F] {
    val success = EitherT.rightT[F, ValidationError](())
    val contractFull = EitherT.leftT[F, Unit](ValidationError.CompanyContractFull: ValidationError)
    override def canCreateSession(id: CompanyContract.Id) = for {
      companyContract <- companyContractRepo.get(id).leftMap(ValidationError.Repo)
      result <- companyContract.data.kind match {
        case Kind.Unlimited => success
        case Kind.OneShot => repo.listByCompanyContract(id, 0, 100)
          .leftMap[ValidationError](ValidationError.Repo)
          .flatMap {
            case Nil => success
            case _ => contractFull
          }
        case _ => contractFull
      }
    } yield result

    override def hasOwnership(id: Id, companyId: Option[Company.Id]) = for {
      formSession <- repo.get(id).leftMap(ValidationError.Repo)
      _ <- formValidation.hasOwnership(formSession.data.testForm, companyId)
    } yield ()
  }

  class Service[F[_] : Monad](repo: Repo[F], validation: Validation[F]) {
    type Result[T] = EitherT[F, ValidationError, T]

    def create(formSession: Create, companyContractId: CompanyContract.Id): Result[Full] = for {
      _ <- validation.canCreateSession(companyContractId)
      createdFormSession <- repo.create(formSession.copy(companyContractId = companyContractId)).leftMap[ValidationError](ValidationError.Repo)
    } yield createdFormSession

    def getById(formId: Id, companyId: Option[Company.Id]): Result[Full] = for {
      _ <- validation.hasOwnership(formId, companyId)
      repoForm <- repo.get(formId).leftMap[ValidationError](ValidationError.Repo)
    } yield repoForm

    def delete(formId: Id, companyId: Option[Company.Id]): Result[Unit] = for {
      _ <- validation.hasOwnership(formId, companyId)
      _ <- repo.delete(formId).leftMap[ValidationError](ValidationError.Repo)
    } yield ()

    def update(form: Update, companyId: Option[Company.Id]): Result[Full] = for {
      _ <- validation.hasOwnership(form.id, companyId)
      result <- repo.update(form).leftMap[ValidationError](ValidationError.Repo)
    } yield result

    def listByCompanyContract(companyContractId: CompanyContract.Id, pageSize: Int, offset: Int): Result[List[Record]] =
      repo.listByCompanyContract(companyContractId, pageSize, offset).leftMap(ValidationError.Repo)
  }
}


