package com.lion.rafiki.domain.company

import cats.implicits.catsSyntaxOptionId
import io.circe.jawn
import io.circe.syntax.EncoderOps
import org.specs2.Specification

class FormCodecSpec extends Specification { def is = s2"""
      Form tree encodings:
        Group correctly encoded: $correctlyEncodedGroup
        Group update correctly encoded: $correctlyEncodedGroupUpdate
        Question correctly encoded: $correctlyEncodedQuestion
        Question correctly encoded wiz answers: $correctlyEncodedQuestionWithAnswers
        Question update correctly encoded: $correctlyEncodedQuestionUpdate
        Text correctly encoded: $correctlyEncodedText
        Text update correctly encoded: $correctlyEncodedTextUpdate

      Form tree decodings:
        Group correctly decoded: $correctlyDecodedGroup
        Group update correctly decoded: $correctlyDecodedGroupUpdate
        Question correctly decoded: $correctlyDecodedQuestion
        Question correctly decoded wiz answers: $correctlyDecodedQuestionWithAnswers
        Question update correctly decoded: $correctlyDecodedQuestionUpdate
        Text correctly decoded: $correctlyDecodedText
        Text update correctly decoded: $correctlyDecodedText

      Question answers encodings:
        FreeText correctly encoded: $correctlyEncodedFreeTextAnswer
        FreeTextWithId correctly encoded: $correctlyEncodedFreeTextWithIdAnswer
        Numeric correctly encoded: $correctlyEncodedNumericAnswer
        NumericWithId correctly encoded: $correctlyEncodedNumericWithIdAnswer

      Question answers decodings:
        FreeText correctly decoded: $correctlyDecodedFreeTextAnswer
        FreeTextWithId correctly decoded: $correctlyDecodedFreeTextWithIdAnswer
        Numeric correctly decoded: $correctlyDecodedNumericAnswer
        NumericWithId correctly decoded: $correctlyDecodedNumericWithIdAnswer

      Form Create:
        Decode Create correctly: $correctlyDecodeFormCreate
"""

  // Form tree encodings
  val correctlyEncodedGroup = Form.Tree.Group(Nil).asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"children":[]}""")
  val correctlyEncodedQuestion = Form.Tree.Question("label", "text", Nil).asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo(
    """{"label":"label","text":"text","answers":[]}"""
  )
  val correctlyEncodedQuestionWithAnswers = Form.Tree.Question("label", "text", List(Form.Tree.Question.Answer.Numeric("label".some, 1), Form.Tree.Question.Answer.FreeText("label".some))).asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo(
    """{"label":"label","text":"text","answers":[{"label":"label","value":1},{"label":"label"}]}"""
  )
  val correctlyEncodedText = Form.Tree.Text("text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"text":"text"}""")

  val correctlyEncodedGroupUpdate = Form.Tree.GroupWithKey(Form.Tree.tagSerial(2), Nil).asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"id":2,"children":[]}""")
  val correctlyEncodedQuestionUpdate = Form.Tree.QuestionWithKey(Form.Tree.tagSerial(3), "label", "text", Nil).asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo(
    """{"id":3,"label":"label","text":"text","answers":[]}"""
  )
  val correctlyEncodedTextUpdate = Form.Tree.TextWithKey(Form.Tree.tagSerial(4), "text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"id":4,"text":"text"}""")

  // Form tree decodings
  val correctlyDecodedGroup = jawn.decode[Form.Tree]("""{"children":[]}""") should beRight(Form.Tree.Group(Nil))
  val correctlyDecodedQuestion = jawn.decode[Form.Tree]("""{"label":"label","text":"text","answers":[]}""") should beRight(Form.Tree.Question("label", "text", Nil))
  val correctlyDecodedQuestionWithAnswers = jawn.decode[Form.Tree]("""{"label":"label","text":"text","answers":[{"label":"label","value":1},{"label":"label"}]}""") should beRight(
    Form.Tree.Question("label", "text", List(Form.Tree.Question.Answer.Numeric("label".some, 1), Form.Tree.Question.Answer.FreeText("label".some)))
  )
  val correctlyDecodedText = jawn.decode[Form.Tree]("""{"text":"text"}""") should beRight(Form.Tree.Text("text"))

  val correctlyDecodedGroupUpdate = jawn.decode[Form.Tree]("""{"id":2,"children":[]}""") should beRight(Form.Tree.GroupWithKey(Form.Tree.tagSerial(2), Nil))
  val correctlyDecodedQuestionUpdate = jawn.decode[Form.Tree]("""{"id":3,"label":"label","text":"text","answers":[]}""") should beRight(Form.Tree.QuestionWithKey(Form.Tree.tagSerial(3), "label", "text", Nil))
  val correctlyDecodedTextUpdate = jawn.decode[Form.Tree]("""{"id":4,"text":"text"}""") should beRight(Form.Tree.TextWithKey(Form.Tree.tagSerial(4), "text"))

  // Question answers encodings
  val correctlyEncodedFreeTextAnswer =  Form.Tree.Question.Answer.FreeText("label".some).asInstanceOf[Form.Tree.Question.Answer].asJson.noSpaces should beEqualTo("""{"label":"label"}""")
  val correctlyEncodedNumericAnswer = Form.Tree.Question.Answer.Numeric(None, 1).asInstanceOf[Form.Tree.Question.Answer].asJson.noSpaces should beEqualTo("""{"label":null,"value":1}""")
  val correctlyEncodedFreeTextWithIdAnswer = Form.Tree.Question.Answer.FreeTextWithId(Form.Tree.Question.Answer.tagSerial(2), "label".some).asInstanceOf[Form.Tree.Question.Answer].asJson.noSpaces should beEqualTo("""{"id":2,"label":"label"}""")
  val correctlyEncodedNumericWithIdAnswer = Form.Tree.Question.Answer.NumericWithId(Form.Tree.Question.Answer.tagSerial(2), None, 1).asInstanceOf[Form.Tree.Question.Answer].asJson.noSpaces should beEqualTo("""{"id":2,"label":null,"value":1}""")

  // Question answers decodings
  val correctlyDecodedFreeTextAnswer = jawn.decode[Form.Tree.Question.Answer]("""{"label":"label"}""") should beRight(Form.Tree.Question.Answer.FreeText("label".some))
  val correctlyDecodedNumericAnswer = jawn.decode[Form.Tree.Question.Answer]("""{"label":null,"value":1}""") should beRight(Form.Tree.Question.Answer.Numeric(None, 1))
  val correctlyDecodedFreeTextWithIdAnswer = jawn.decode[Form.Tree.Question.Answer]("""{"id":2,"label":"label"}""") should beRight(Form.Tree.Question.Answer.FreeTextWithId(Form.Tree.Question.Answer.tagSerial(2), "label".some))
  val correctlyDecodedNumericWithIdAnswer = jawn.decode[Form.Tree.Question.Answer]("""{"id":2,"value":1}""") should beRight(Form.Tree.Question.Answer.NumericWithId(Form.Tree.Question.Answer.tagSerial(2), None, 1))

  // Form create
  val correctlyDecodeFormCreate = jawn.decode[Form.Create](
    """{
      |"name" : "my form",
      |"description" : "my description",
      |"tree" : {
      |   "children" : [
      |       { "label" : "q-000", "text" : "Q1", "answers": [{"id":2,"label": "num_value1","value":1}, {"id":3,"label": "num_value2","value":2}] },
      |       { "label" : "q-001", "text" : "Q2", "answers": [{"id":2,"value":1}, {"id":3, "label": "freetext"}] }
      |   ]
      |}}""".stripMargin
  ) should beRight(Form(
    None,
    "my form",
    "my description".some,
    Form.Tree.Group(List(
      Form.Tree.Question("q-000", "Q1", List(
        Form.Tree.Question.Answer.NumericWithId(Form.Tree.Question.Answer.tagSerial(2), "num_value1".some, 1),
        Form.Tree.Question.Answer.NumericWithId(Form.Tree.Question.Answer.tagSerial(3), "num_value2".some, 2))
      ),
      Form.Tree.Question("q-001", "Q2", List(
        Form.Tree.Question.Answer.NumericWithId(Form.Tree.Question.Answer.tagSerial(2), None, 1),
        Form.Tree.Question.Answer.FreeTextWithId(Form.Tree.Question.Answer.tagSerial(3), "freetext".some))
      )
    )).some)
  )
}
