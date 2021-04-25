package com.lion.rafiki.domain.company

import com.lion.rafiki.domain.{CompanyContract, WithId}
import shapeless.tag
import shapeless.tag.@@

import java.time.ZonedDateTime

case class FormSession[T](companyContract: CompanyContract.Id, testForm: Form.Id, name: String, startDate: Option[ZonedDateTime], endDate: Option[ZonedDateTime]) {
  def withId(id: FormSession.Id) = WithId(id, this)
}

object FormSession {
  type Id = Long @@ FormSession[_]
  val tagSerial = tag[FormSession[_]](_: Long)


}


