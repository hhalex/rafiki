package com.lion.rafiki.domain.company

import cats.syntax.all._
import com.lion.rafiki.domain.{
    Company,
    RepoError,
    TaggedId,
    ValidationError,
    WithId
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

type QuestionAnswerP = QuestionAnswer.Numeric | QuestionAnswer.FreeText
type QuestionAnswer = QuestionAnswerP | WithId[QuestionAnswer.Id, QuestionAnswerP]

trait QuestionAnswerId
object QuestionAnswer extends TaggedId[QuestionAnswerId] {

    extension [T <: QuestionAnswerP](answer: T) {
        def withId(id: QuestionAnswer.Id) = WithId(id, answer)
    }

    case class FreeText(label: Option[String])
    case class Numeric(label: Option[String], value: Int)

    implicit val questionAnswerPDecoder: Decoder[QuestionAnswerP] =
        deriveDecoder[Numeric].widen.or(deriveDecoder[FreeText].widen)
    implicit val questionAnswerWithIdDecoder: Decoder[WithId[Id, QuestionAnswerP]] = WithId.decoder
    implicit val questionAnswerDecoder: Decoder[QuestionAnswer] =
        Decoder[WithId[Id, QuestionAnswerP]].widen.or(Decoder[QuestionAnswerP].widen)

    implicit val questionAnswerPEncoder: Encoder[QuestionAnswerP] = Encoder.instance {
        case r: Numeric      => r.asJson(deriveEncoder[Numeric])
        case r: FreeText     => r.asJson(deriveEncoder[FreeText])
    }
    implicit val questionAnswerWithIdEncoder: Encoder[WithId[Id, QuestionAnswerP]] = WithId.encoder
    implicit val questionAnswerEncoder: Encoder[QuestionAnswer] = Encoder.instance {
        case wId @ WithId(id, a: QuestionAnswer) => wId.asJson
        case a => a.asJson
    }
}