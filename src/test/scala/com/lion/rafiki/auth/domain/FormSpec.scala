package com.lion.rafiki.auth.domain

import com.lion.rafiki.domain.Form
import io.circe.syntax.EncoderOps
import org.specs2.Specification

class FormCodecSpec extends Specification { def is = s2"""
      Form encodings:
        Group correctly encoded: $correctlyEncodedGroup
        Group update correctly encoded: $correctlyEncodedGroupUpdate
        Question correctly encoded: $correctlyEncodedQuestion
        Question update correctly encoded: $correctlyEncodedQuestionUpdate
        Text correctly encoded: $correctlyEncodedText
        Text update correctly encoded: $correctlyEncodedTextUpdate"""

  val correctlyEncodedGroup = Form.createGroup().asJson.noSpaces should beEqualTo("""{"id":null,"children":[]}""")
  val correctlyEncodedQuestion = Form.createQuestion("label", "text").asJson.noSpaces should beEqualTo(
    """{"id":null,"label":"label","text":"text"}"""
  )
  val correctlyEncodedText = Form.createText("text").asJson.noSpaces should beEqualTo("""{"id":null,"text":"text"}""")

  val correctlyEncodedGroupUpdate = Form.updateGroup(Form.tagSerial(2)).asJson.noSpaces should beEqualTo("""{"id":2,"children":[]}""")
  val correctlyEncodedQuestionUpdate = Form.updateQuestion(Form.tagSerial(3), "label", "text").asJson.noSpaces should beEqualTo(
    """{"id":3,"label":"label","text":"text"}"""
  )
  val correctlyEncodedTextUpdate = Form.updateText(Form.tagSerial(4), "text").asJson.noSpaces should beEqualTo("""{"id":4,"text":"text"}""")
}
