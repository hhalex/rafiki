package com.lion.rafiki.domain.company

import cats.implicits.catsSyntaxOptionId
import io.circe.jawn
import io.circe.syntax.EncoderOps
import org.specs2.mutable._
import com.lion.rafiki.domain.Fix
import com.lion.rafiki.domain.WithIdF
import com.lion.rafiki.domain.company.FormTree.given
import com.lion.rafiki.domain.company.FormTreeP
import com.lion.rafiki.domain.company.FormTreeWithId
import com.lion.rafiki.domain.company.Form.given
import io.circe.Encoder
import com.lion.rafiki.domain.WithId

class FormCodecSpec extends Specification {
  "Form Create" >> {
    "Decode Create correctly" >> {
      jawn.decode[Form.Create](
        """{
          |"name" : "form",
          |"description" : "description",
          |"tree" : {
          |   "children" : []
          |}}""".stripMargin.replace(" ", "").replace("\n", "")
      ) should beRight(
        Form[FormTree](
          None,
          "form",
          "description".some,
          Fix(FormTreeP.Group[FormTree](Nil)).some
        )
      )
    }
    "Encode Record correctly" >> {
      Form[FormTree.Key](
          None,
          "form",
          "description".some,
          (FormTree.tag(2), FormTree.Kind.Text).some
      ).withId(Form.tag(3)).asJson.noSpaces must_== """{
        |"company": null,
        |"name" : "form",
        |"description" : "description",
        |"tree" : [2, "text"],
        |"id": 3
        |}""".stripMargin.replace(" ", "").replace("\n", "")
    }

    "Encode Full correctly" >> {
      Form[FormTree.Record](
          None,
          "form",
          "description".some,
          Fix(FormTreeP.Text("text").withId(FormTree.tag(4)).asInstanceOf[WithIdF[FormTree.Id, FormTreeP][FormTreeWithId]]).some
      ).withId(Form.tag(3)).asJson.noSpaces must_== """{
        |"company": null,
        |"name" : "form",
        |"description" : "description",
        |"tree" : {"text": "text", "id": 4},
        |"id": 3
        |}""".stripMargin.replace(" ", "").replace("\n", "")
    }
  }
}
