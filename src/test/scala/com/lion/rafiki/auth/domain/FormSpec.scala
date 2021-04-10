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

  val correctlyEncodedGroup = Form.Tree.Group(Nil).asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"id":null,"children":[]}""")
  val correctlyEncodedQuestion = Form.Tree.Question("label", "text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo(
    """{"id":null,"label":"label","text":"text"}"""
  )
  val correctlyEncodedText = Form.Tree.Text("text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"id":null,"text":"text"}""")

  val correctlyEncodedGroupUpdate = Form.Tree.GroupWithKey(Form.Tree.tagSerial(2), Nil).asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"id":2,"children":[]}""")
  val correctlyEncodedQuestionUpdate = Form.Tree.QuestionWithKey(Form.Tree.tagSerial(3), "label", "text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo(
    """{"id":3,"label":"label","text":"text"}"""
  )
  val correctlyEncodedTextUpdate = Form.Tree.TextWithKey(Form.Tree.tagSerial(4), "text").asInstanceOf[Form.Tree].asJson.noSpaces should beEqualTo("""{"id":4,"text":"text"}""")
}
