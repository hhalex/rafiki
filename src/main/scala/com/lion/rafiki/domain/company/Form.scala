package com.lion.rafiki.domain.company

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import com.lion.rafiki.domain.{Company, RepoError, TaggedId, ValidationError, WithId}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

case class Form[T](
    company: Option[Company.Id],
    name: String,
    description: Option[String],
    tree: Option[T]
) {
  def withId(id: Form.Id) = WithId(id, this)
}
trait FormId
object Form extends TaggedId[FormId] {
  import Company.Id.given
  import FormTree.given
  import FormTree.Id.given
  import Id.given
  given [T: Decoder]: Decoder[Form[T]] = deriveDecoder
  given [T: Encoder]: Encoder[Form[T]] = deriveEncoder

  given Decoder[Create] = deriveDecoder
  given Encoder[Full] = WithId.deriveEncoder
  given Encoder[Record] = WithId.deriveEncoder

  type Create = Form[FormTree]
  type Update = WithId[Id, Create]
  type Record = WithId[Id, Form[FormTree.Key]]
  type Full = WithId[Id, Form[FormTree.Record]]

  trait Repo[F[_]] {
    type Result[T] = EitherT[F, RepoError, T]
    def create(form: Create): Result[Full]
    def update(form: Update): Result[Full]
    def get(id: Id): Result[Full]
    def delete(id: Id): Result[Unit]
    def list(pageSize: Int, offset: Int): Result[List[Record]]
    def listByCompany(
        company: Company.Id,
        pageSize: Int,
        offset: Int
    ): Result[List[Record]]
  }

  trait Validation[F[_]] {
    def hasOwnership(
        formId: Form.Id,
        companyId: Option[Company.Id]
    ): EitherT[F, ValidationError, Full]
  }

  class FromRepoValidation[F[_]: Monad](repo: Repo[F]) extends Validation[F] {
    val notAllowed = EitherT
      .leftT[F, Form.Full](ValidationError.NotAllowed)
      .leftWiden[ValidationError]
    override def hasOwnership(
        formId: Form.Id,
        companyId: Option[Company.Id]
    ): EitherT[F, ValidationError, Full] = for
      repoForm <- repo.get(formId).leftMap(ValidationError.Repo)
      success = EitherT.rightT[F, ValidationError](repoForm)
      _ <- (companyId, repoForm.data.company) match {
        case (Some(id), Some(formOwnerId)) if id == formOwnerId => success
        case (None, _)                                          => success
        case _                                                  => notAllowed
      }
    yield repoForm
  }

  class Service[F[_]: Monad](
      repo: Repo[F],
      inviteAnswerRepo: InviteAnswer.Repo[F],
      validation: Validation[F]
  ) {
    type Result[T] = EitherT[F, ValidationError, T]
    def create(form: Create, companyId: Option[Company.Id]): Result[Full] =
      (for
        createdForm <- repo.create(form.copy(company = companyId))
        answerTableName = s"form${createdForm.id}_answers"
          .asInstanceOf[InviteAnswer.TableName]
        _ <- createdForm.data.tree.traverse(t =>
          inviteAnswerRepo.overrideAnswerTable(answerTableName, FormTree.labels(t))
        )
      yield createdForm).leftMap(ValidationError.Repo)

    def getById(formId: Id, companyId: Option[Company.Id]): Result[Full] = for
      _ <- validation.hasOwnership(formId, companyId)
      repoForm <- repo
        .get(formId)
        .leftMap[ValidationError](ValidationError.Repo)
    yield repoForm

    def delete(formId: Id, companyId: Option[Company.Id]): Result[Unit] = for
      _ <- validation.hasOwnership(formId, companyId)
      _ <- repo.delete(formId).leftMap[ValidationError](ValidationError.Repo)
    yield ()

    def update(form: Update, companyId: Option[Company.Id]): Result[Full] =
      for
        formDB <- validation.hasOwnership(form.id, companyId)
        result <- repo
          .update(form.mapData(_.copy(company = formDB.data.company)))
          .leftMap[ValidationError](ValidationError.Repo)
        answerTableName = s"form${result.id}_answers"
          .asInstanceOf[InviteAnswer.TableName]
        _ <- result.data.tree
          .traverse(t =>
            inviteAnswerRepo.overrideAnswerTable(answerTableName, FormTree.labels(t))
          )
          .leftMap(ValidationError.Repo)
      yield result

    def listByCompany(
        companyId: Company.Id,
        pageSize: Int,
        offset: Int
    ): Result[List[Record]] =
      repo
        .listByCompany(companyId, pageSize, offset)
        .leftMap(ValidationError.Repo)
  }
}
