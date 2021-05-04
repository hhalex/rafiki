package com.lion.rafiki.domain.company

import cats.implicits.catsSyntaxOptionId
import io.circe.jawn
import io.circe.syntax.EncoderOps
import org.specs2.mutable._
import com.lion.rafiki.domain.company.Form

class FormCodecSpec extends Specification {
  "Form tree encodings" >> {
    "Group correctly encoded" >> {
      Form.Tree
        .Group(Nil)
        .asInstanceOf[Form.Tree]
        .asJson
        .noSpaces.should(beEqualTo("""{"children":[]}"""))
    }
    "Group update correctly encoded" >> {
      Form.Tree
        .Group(Nil)
        .withId(Form.Tree.tag(2))
        .asInstanceOf[Form.Tree]
        .asJson
        .noSpaces should beEqualTo("""{"id":2,"children":[]}""")
    }
    "Question correctly encoded" >> {
      Form.Tree
        .Question("label", "text", Nil)
        .asInstanceOf[Form.Tree]
        .asJson
        .noSpaces should beEqualTo(
        """{"label":"label","text":"text","answers":[]}"""
      )
    }
    "Question correctly encoded wiz answers" >> {
      Form.Tree
        .Question(
          "label",
          "text",
          List(
            Form.Tree.Question.Answer.Numeric("label".some, 1),
            Form.Tree.Question.Answer.FreeText("label".some)
          )
        )
        .asInstanceOf[Form.Tree]
        .asJson
        .noSpaces should beEqualTo(
        """{"label":"label","text":"text","answers":[{"label":"label","value":1},{"label":"label"}]}"""
      )
    }
    "Question update correctly encoded" >> {
      Form.Tree
        .Question("label", "text", Nil)
        .withId(Form.Tree.tag(3))
        .asInstanceOf[Form.Tree]
        .asJson
        .noSpaces should beEqualTo(
        """{"id":3,"label":"label","text":"text","answers":[]}"""
      )
    }
    "Text correctly encoded" >> {
      Form.Tree
        .Text("text")
        .asInstanceOf[Form.Tree]
        .asJson
        .noSpaces should beEqualTo("""{"text":"text"}""")
    }
    "Text update correctly encoded" >> {
      Form.Tree
        .Text("text")
        .withId(Form.Tree.tag(4))
        .asInstanceOf[Form.Tree]
        .asJson
        .noSpaces should beEqualTo("""{"id":4,"text":"text"}""")
    }

    "Form tree decodings" >> {
      "Group correctly decoded" >> {
        jawn.decode[Form.Tree]("""{"children":[]}""") should beRight(
          Form.Tree.Group(Nil)
        )
      }
      "Group update correctly decoded" >> {
        jawn.decode[Form.Tree]("""{"id":2,"children":[]}""") should beRight(
          Form.Tree.Group(Nil).withId(Form.Tree.tag(2))
        )
      }
      "Question correctly decoded" >> {
        jawn.decode[Form.Tree](
          """{"label":"label","text":"text","answers":[]}"""
        ) should beRight(Form.Tree.Question("label", "text", Nil))
      }
      "Question correctly decoded wiz answers" >> {
        jawn.decode[Form.Tree](
          """{"label":"label","text":"text","answers":[{"label":"label","value":1},{"label":"label"}]}"""
        ) should beRight(
          Form.Tree.Question(
            "label",
            "text",
            List(
              Form.Tree.Question.Answer.Numeric("label".some, 1),
              Form.Tree.Question.Answer.FreeText("label".some)
            )
          )
        )
      }
      "Question update correctly decoded" >> {
        jawn.decode[Form.Tree](
          """{"id":3,"label":"label","text":"text","answers":[]}"""
        ) should beRight(
          Form.Tree.Question("label", "text", Nil).withId(Form.Tree.tag(3))
        )
      }
      "Text correctly decoded" >> {
        jawn.decode[Form.Tree]("""{"text":"text"}""") should beRight(
          Form.Tree.Text("text")
        )
      }
      "Text update correctly decoded" >> {
        jawn.decode[Form.Tree]("""{"id":4,"text":"text"}""") should beRight(
          Form.Tree.Text("text").withId(Form.Tree.tag(4))
        )
      }
    }

    "Question answers encodings" >> {
      "FreeText correctly encoded" >> {
        Form.Tree.Question.Answer
          .FreeText("label".some)
          .asInstanceOf[Form.Tree.Question.Answer]
          .asJson
          .noSpaces should beEqualTo("""{"label":"label"}""")
      }
      "FreeTextWithId correctly encoded" >> {
        Form.Tree.Question.Answer
          .FreeTextWithId(Form.Tree.Question.Answer.tag(2), "label".some)
          .asInstanceOf[Form.Tree.Question.Answer]
          .asJson
          .noSpaces should beEqualTo("""{"id":2,"label":"label"}""")
      }
      "Numeric correctly encoded" >> {
        Form.Tree.Question.Answer
          .Numeric(None, 1)
          .asInstanceOf[Form.Tree.Question.Answer]
          .asJson
          .noSpaces should beEqualTo("""{"label":null,"value":1}""")
      }
      "NumericWithId correctly encoded" >> {
        Form.Tree.Question.Answer
          .NumericWithId(Form.Tree.Question.Answer.tag(2), None, 1)
          .asInstanceOf[Form.Tree.Question.Answer]
          .asJson
          .noSpaces should beEqualTo("""{"id":2,"label":null,"value":1}""")
      }
    }

    "Question answers decodings" >> {
      "FreeText correctly decoded" >> {
        jawn.decode[Form.Tree.Question.Answer](
          """{"label":"label"}"""
        ) should beRight(Form.Tree.Question.Answer.FreeText("label".some))
      }
      "FreeTextWithId correctly decoded" >> {
        jawn.decode[Form.Tree.Question.Answer](
          """{"id":2,"label":"label"}"""
        ) should beRight(
          Form.Tree.Question.Answer
            .FreeTextWithId(Form.Tree.Question.Answer.tag(2), "label".some)
        )
      }
      "Numeric correctly decoded" >> {
        jawn.decode[Form.Tree.Question.Answer](
          """{"label":null,"value":1}"""
        ) should beRight(Form.Tree.Question.Answer.Numeric(None, 1))
      }
      "NumericWithId correctly decoded" >> {
        jawn
          .decode[Form.Tree.Question.Answer](
            """{"id":2,"value":1}"""
          ) should beRight(
          Form.Tree.Question.Answer
            .NumericWithId(Form.Tree.Question.Answer.tag(2), None, 1)
        )
      }
    }

    "Form Create" >> {
      "Decode Create correctly" >> {
        jawn.decode[Form.Create](
          """{
            |"name" : "my form",
            |"description" : "my description",
            |"tree" : {
            |   "children" : [
            |       { "label" : "q-000", "text" : "Q1", "answers": [{"id":2,"label": "num_value1","value":1}, {"id":3,"label": "num_value2","value":2}] },
            |       { "label" : "q-001", "text" : "Q2", "answers": [{"id":2,"value":1}, {"id":3, "label": "freetext"}] }
            |   ]
            |}}""".stripMargin
        ) should beRight(
          Form(
            None,
            "my form",
            "my description".some,
            Form.Tree
              .Group(
                List(
                  Form.Tree.Question(
                    "q-000",
                    "Q1",
                    List(
                      Form.Tree.Question.Answer.NumericWithId(
                        Form.Tree.Question.Answer.tag(2),
                        "num_value1".some,
                        1
                      ),
                      Form.Tree.Question.Answer.NumericWithId(
                        Form.Tree.Question.Answer.tag(3),
                        "num_value2".some,
                        2
                      )
                    )
                  ),
                  Form.Tree.Question(
                    "q-001",
                    "Q2",
                    List(
                      Form.Tree.Question.Answer
                        .NumericWithId(
                          Form.Tree.Question.Answer.tag(2),
                          None,
                          1
                        ),
                      Form.Tree.Question.Answer.FreeTextWithId(
                        Form.Tree.Question.Answer.tag(3),
                        "freetext".some
                      )
                    )
                  )
                )
              )
              .some
          )
        )
      }
    }
  }
}
