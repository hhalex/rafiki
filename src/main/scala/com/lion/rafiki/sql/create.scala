package com.lion.rafiki.sql

import doobie.implicits.toSqlInterpolator
import cats.implicits._
import doobie.implicits._

object create {
  val users =
    sql"""CREATE TABLE IF NOT EXISTS users (
       id                 serial        PRIMARY KEY,
       firstname          text,
       name               text,
       email              text          NOT NULL UNIQUE,
       password           text          NOT NULL
    )""".update

  val companies =
    sql"""CREATE TABLE IF NOT EXISTS companies (
       id                 serial        PRIMARY KEY,
       name               text          NOT NULL,
       rh_user            serial        NOT NULL references users(id)
    )""".update

  val companyContracts =
    sql"""CREATE TABLE IF NOT EXISTS company_contracts (
       id                 serial        PRIMARY KEY,
       company            serial        NOT NULL references companies(id),
       kind               text          NOT NULL
    )""".update

  val allTables = (users.run, companies.run, companyContracts.run).mapN(_ + _ + _)
}
