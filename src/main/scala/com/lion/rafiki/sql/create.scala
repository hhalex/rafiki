package com.lion.rafiki.sql

import doobie.implicits.toSqlInterpolator

object create {
  val users =
    sql"""CREATE TABLE IF NOT EXISTS users (
       id                 uuid          NOT NULL DEFAULT gen_random_uuid(),
       firstname          text,
       name               text,
       email              text          NOT NULL,
       password           text          NOT NULL
    )""".update
}
