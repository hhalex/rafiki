package com.lion.rafiki.auth

import cats._
import tsec.authorization.{AuthGroup, SimpleAuthEnum}

final case class Role(roleRepr: String)

object Role extends SimpleAuthEnum[Role, String] {
  val Admin: Role = Role("Admin")
  val Company: Role = Role("Company")
  val Employee: Role = Role("Employee")

  override val values: AuthGroup[Role] = AuthGroup(Admin, Company, Employee)

  override def getRepr(t: Role): String = t.roleRepr

  implicit val eqRole: Eq[Role] = Eq.fromUniversalEquals[Role]
}
