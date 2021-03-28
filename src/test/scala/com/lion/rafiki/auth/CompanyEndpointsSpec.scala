package com.lion.rafiki.auth

import com.lion.rafiki.domain.{Company, User}
import io.circe.jawn
import org.specs2.Specification

class CompanyEndpointsSpec extends Specification { def is = s2"""
      Company Endpoints:
        $test
    """
  val decodedCompanyCreate = jawn.decode[Company.Create]("{\"name\":\"New Company\",\"rh_user\":{\"username\":\"adress@company.com\",\"password\":\"pass\"}}")

  val test = decodedCompanyCreate should beRight(Company[User[String]](
    "New Company", User[String](
     username = "adress@company.com", password = "pass", firstname = None, name = None
    )))
}
