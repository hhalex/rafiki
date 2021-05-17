package com.lion.rafiki.domain.company

import cats.syntax.all._
import com.lion.rafiki.domain.{
    Company,
    RepoError,
    TaggedId,
    ValidationError,
    WithId,
    OrWithId
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

enum QuestionAnswerP:
    case FreeText(label: Option[String])
    case Numeric(label: Option[String], value: Int)
    def withId(id: QuestionAnswer.Id) = WithId(id, this)

object QuestionAnswerP:
    given Decoder[QuestionAnswerP] = deriveDecoder[Numeric].widen.or(deriveDecoder[FreeText].widen)
    given Encoder[QuestionAnswerP] = Encoder.instance {
        case r: Numeric      => r.asJson(deriveEncoder[Numeric])
        case r: FreeText     => r.asJson(deriveEncoder[FreeText])
    }

type QuestionAnswer = OrWithId[QuestionAnswer.Id, QuestionAnswerP]
type QuestionAnswerWithId = WithId[QuestionAnswer.Id, QuestionAnswerP]

trait QuestionAnswerId
object QuestionAnswer extends TaggedId[QuestionAnswerId] {

    import Id.given
    given Decoder[QuestionAnswerWithId] = WithId.deriveDecoder
    given Encoder[QuestionAnswerWithId] = WithId.deriveEncoder
    given Decoder[QuestionAnswer] =
        Decoder[QuestionAnswerWithId].widen
            .or(Decoder[QuestionAnswerP].widen)
    given Encoder[QuestionAnswer] = Encoder.instance {
        case wId@WithId(_, _) => wId.asJson
        case a: QuestionAnswerP => a.asJson
    }
}