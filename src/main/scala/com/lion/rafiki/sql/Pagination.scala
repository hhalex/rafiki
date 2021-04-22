package com.lion.rafiki.sql

import doobie._
import doobie.implicits._

trait SQLPagination {
  def limit[A: Read](lim: Long)(q: Query0[A]): Query0[A] =
    (q.toFragment ++ fr" LIMIT $lim").query

  def paginate[A: Read](lim: Long, offset: Long)(q: Query0[A]): Query0[A] =
    (q.toFragment ++ fr" LIMIT $lim OFFSET $offset").query
}

object SQLPagination extends SQLPagination
