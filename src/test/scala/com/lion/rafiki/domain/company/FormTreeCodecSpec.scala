package com.lion.rafiki.domain.company

import cats.implicits.catsSyntaxOptionId
import io.circe.jawn
import io.circe.syntax.EncoderOps
import org.specs2.mutable._
import com.lion.rafiki.domain.Fix
import com.lion.rafiki.domain.company.FormTree.given
import com.lion.rafiki.domain.company.FormTreeP
import com.lion.rafiki.domain.company.QuestionAnswer.given
import io.circe.Encoder
import io.circe.Json

class FormTreeCodecSpec extends Specification {
  val formTree: FormTree = Fix(FormTreeP.Group(
    List(
      Fix(FormTreeP.Question(
        "q-000",
        "Q1",
        List(
          QuestionAnswerP.Numeric(
            "num_value1".some,
            1
          ).withId(QuestionAnswer.tag(2)),
          QuestionAnswerP.Numeric(
            "num_value2".some,
            2
          ).withId(QuestionAnswer.tag(3))
        )
      )),
      Fix(FormTreeP.Question(
        "q-001",
        "Q2",
        List(
          QuestionAnswerP
            .Numeric(
              None,
              1
            ).withId(QuestionAnswer.tag(2)),
          QuestionAnswerP.FreeText(
            "freetext".some
          ).withId(QuestionAnswer.tag(3))
        )
      ))
    )
  ))
  val formTreeJson = """{
    |   "children" : [
    |       { "label" : "q-000", "text" : "Q1", "answers": [{"label": "num_value1","value":1,"id":2}, {"label": "num_value2","value":2,"id":3}] },
    |       { "label" : "q-001", "text" : "Q2", "answers": [{"label": null, "value":1,"id":2}, {"label": "freetext","id":3}] }
    |   ]
    |}""".stripMargin.replace(" ", "").replace("\n", "")

  "FormTree decoder" >> {
    jawn.decode[FormTree](formTreeJson) should beRight(formTree)
  }

  "FormTree encoder" >> {
    formTree.asJson.noSpaces must_== formTreeJson
  }

  "Form tree encodings" >> {
    "Group correctly encoded" >> {
      FormTreeP
        .Group(Nil)
        .asInstanceOf[FormTreeP[FormTree]]
        .asJson
        .noSpaces must_== """{"children":[]}"""
    }
    "Group update correctly encoded" >> {
      FormTreeP
        .Group(Nil)
        .withId(FormTree.tag(2))
        .asInstanceOf[FormTreeWithIdF[FormTree]]
        .asJson
        .noSpaces must_== """{"children":[],"id":2}"""
    }
    "Question correctly encoded" >> {
      FormTreeP
        .Question("label", "text", Nil)
        .asInstanceOf[FormTreeP[FormTree]]
        .asJson
        .noSpaces must_== """{"label":"label","text":"text","answers":[]}"""
    }
    "Question correctly encoded wiz answers" >> {
      FormTreeP
        .Question(
          "label",
          "text",
          List(
            QuestionAnswerP.Numeric("label".some, 1),
            QuestionAnswerP.FreeText("label".some)
          )
        )
        .asInstanceOf[FormTreeP[String]]
        .asJson
        .noSpaces must_== """{"label":"label","text":"text","answers":[{"label":"label","value":1},{"label":"label"}]}"""
    }
    "Question update correctly encoded" >> {
       FormTreeP
        .Question("label", "text", Nil)
        .withId(FormTree.tag(3))
        .asInstanceOf[FormTreeWithIdF[String]]
        .asJson
        .noSpaces must_== """{"label":"label","text":"text","answers":[],"id":3}"""
    }
    "Text correctly encoded" in {
      FormTreeP
        .Text("text")
        .asInstanceOf[FormTreeP[FormTree]]
        .asJson
        .noSpaces must_== """{"text":"text"}"""
    }
    "Text update correctly encoded" >> {
      FormTreeP
        .Text("text")
        .withId(FormTree.tag(4))
        .asInstanceOf[FormTreeWithIdF[FormTree]]
        .asJson
        .noSpaces must_== """{"text":"text","id":4}"""
    }
  }

  "Form tree decodings" >> {
    "Group correctly decoded" >> {
      jawn.decode[FormTreeP[FormTree]]("""{"children":[]}""") should beRight(
        FormTreeP.Group(Nil)
      )
    }
    "Group update correctly decoded" >> {
      jawn.decode[FormTreeWithIdF[FormTree]]("""{"id":2,"children":[]}""") should beRight(
        FormTreeP.Group(Nil).withId(FormTree.tag(2))
      )
    }
    "Question correctly decoded" >> {
      jawn.decode[FormTreeP[FormTree]](
        """{"label":"label","text":"text","answers":[]}"""
      ) should beRight(FormTreeP.Question("label", "text", Nil))
    }
    "Question correctly decoded wiz answers" >> {
      jawn.decode[FormTreeP[FormTree]](
        """{"label":"label","text":"text","answers":[{"label":"label","value":1},{"label":"label"}]}"""
      ) should beRight(
        FormTreeP.Question(
          "label",
          "text",
          List(
            QuestionAnswerP.Numeric("label".some, 1),
            QuestionAnswerP.FreeText("label".some)
          )
        )
      )
    }
    "Question update correctly decoded" >> {
      jawn.decode[FormTreeWithIdF[FormTree]](
        """{"id":3,"label":"label","text":"text","answers":[]}"""
      ) should beRight(
        FormTreeP.Question("label", "text", Nil).withId(FormTree.tag(3))
      )
    }
    "Text correctly decoded" >> {
      jawn.decode[FormTreeP[FormTree]]("""{"text":"text"}""") should beRight(
        FormTreeP.Text("text")
      )
    }
    "Text update correctly decoded" >> {
      jawn.decode[FormTreeWithIdF[FormTree]]("""{"id":4,"text":"text"}""") should beRight(
        FormTreeP.Text("text").withId(FormTree.tag(4))
      )
    }
  }

  "Question answers encodings" >> {
    "FreeText correctly encoded" >> {
      QuestionAnswerP
        .FreeText("label".some)
        .asInstanceOf[QuestionAnswerP]
        .asJson
        .noSpaces must_== """{"label":"label"}"""
    }
    "FreeText/WithId correctly encoded" >> {
      QuestionAnswerP
        .FreeText("label".some).withId(QuestionAnswer.tag(2))
        .asInstanceOf[QuestionAnswerWithId]
        .asJson
        .noSpaces must_== """{"label":"label","id":2}"""
    }
    "Numeric correctly encoded" >> {
      QuestionAnswerP
        .Numeric(None, 1)
        .asInstanceOf[QuestionAnswerP]
        .asJson
        .noSpaces must_== """{"label":null,"value":1}"""
    }
    "Numeric/WithId correctly encoded" >> {
      QuestionAnswerP
        .Numeric(None, 1).withId(QuestionAnswer.tag(2))
        .asInstanceOf[QuestionAnswerWithId]
        .asJson
        .noSpaces must_== """{"label":null,"value":1,"id":2}"""
    }
  }

  "Question answers decodings" >> {
    "FreeText correctly decoded" >> {
      jawn.decode[QuestionAnswerP](
        """{"label":"label"}"""
      ) should beRight(QuestionAnswerP.FreeText("label".some))
    }
    "FreeText/WithId correctly decoded" >> {
      jawn.decode[QuestionAnswerWithId](
        """{"id":2,"label":"label"}"""
      ) should beRight(
        QuestionAnswerP
          .FreeText("label".some).withId(QuestionAnswer.tag(2))
      )
    }
    "Numeric correctly decoded" >> {
      jawn.decode[QuestionAnswerP](
        """{"label":null,"value":1}"""
      ) should beRight(QuestionAnswerP.Numeric(None, 1))
    }
    "NumericWithId correctly decoded" >> {
      jawn
        .decode[QuestionAnswerWithId](
          """{"id":2,"value":1}"""
        ) should beRight(
        QuestionAnswerP
          .Numeric(None, 1).withId(QuestionAnswer.tag(2))
      )
    }
  }
}
