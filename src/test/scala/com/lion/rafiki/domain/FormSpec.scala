package com.lion.rafiki.domain

import cats.implicits.catsSyntaxOptionId
import io.circe.jawn
import io.circe.syntax.EncoderOps
import org.specs2.Specification

class FormCodecSpec extends Specification { def is = s2"""
      Form encodings:
        Group correctly encoded: $correctlyEncodedGroup
        Group update correctly encoded: $correctlyEncodedGroupUpdate
        Question correctly encoded: $correctlyEncodedQuestion
        Question update correctly encoded: $correctlyEncodedQuestionUpdate
        Text correctly encoded: $correctlyEncodedText
        Text update correctly encoded: $correctlyEncodedTextUpdate

      Form decodings:
        Group correctly decoded: $correctlyDecodedGroup
        Group update correctly decoded: $correctlyDecodedGroupUpdate
        Question correctly decoded: $correctlyDecodedQuestion
        Question update correctly decoded: $correctlyDecodedQuestionUpdate
        Text correctly decoded: $correctlyDecodedText
        Text update correctly decoded: $correctlyDecodedTextUpdate

      Form Create:
        Decode Create correctly: $correctlyDecodeFormCreate
        """

  val correctlyEncodedGroup = Form.Tree.Group(Nil).asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"children":[]}""")
  val correctlyEncodedQuestion = Form.Tree.Question("label", "text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo(
    """{"label":"label","text":"text"}"""
  )
  val correctlyEncodedText = Form.Tree.Text("text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"text":"text"}""")

  val correctlyEncodedGroupUpdate = Form.Tree.GroupWithKey(Form.Tree.tagSerial(2), Nil).asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"id":2,"children":[]}""")
  val correctlyEncodedQuestionUpdate = Form.Tree.QuestionWithKey(Form.Tree.tagSerial(3), "label", "text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo(
    """{"id":3,"label":"label","text":"text"}"""
  )
  val correctlyEncodedTextUpdate = Form.Tree.TextWithKey(Form.Tree.tagSerial(4), "text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"id":4,"text":"text"}""")

  val correctlyDecodedGroup = jawn.decode[Form.Tree]("""{"children":[]}""") should beRight(Form.Tree.Group(Nil))
  val correctlyDecodedQuestion = jawn.decode[Form.Tree]("""{"label":"label","text":"text"}""") should beRight(Form.Tree.Question("label", "text"))
  val correctlyDecodedText = jawn.decode[Form.Tree]("""{"text":"text"}""") should beRight(Form.Tree.Text("text"))

  val correctlyDecodedGroupUpdate = jawn.decode[Form.Tree]("""{"id":2,"children":[]}""") should beRight(Form.Tree.GroupWithKey(Form.Tree.tagSerial(2), Nil))
  val correctlyDecodedQuestionUpdate = jawn.decode[Form.Tree]("""{"id":3,"label":"label","text":"text"}""") should beRight(Form.Tree.QuestionWithKey(Form.Tree.tagSerial(3), "label", "text"))
  val correctlyDecodedTextUpdate = jawn.decode[Form.Tree]("""{"id":4,"text":"text"}""") should beRight(Form.Tree.TextWithKey(Form.Tree.tagSerial(4), "text"))


  val correctlyDecodeFormCreate = jawn.decode[Form.Create](
    "{ \"name\" : \"my form\", \"description\" : \"my description\", \"tree\" : {  \"children\" : [{ \"label\" : \"q-000\", \"text\" : \"Q1\" },  { \"label\" : \"q-001\", \"text\" : \"Q2\" }] }}"
  ) should beRight(Form(None, "my form", "my description".some, Form.Tree.Group(List(Form.Tree.Question("q-000", "Q1"), Form.Tree.Question("q-001", "Q2"))).some))
}
