package com.lion.rafiki

import com.lion.rafiki.domain.TaggedId
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.io._

package object endpoints {
  class IdVar[Id] (tag: Long => Id) {
    def unapply(str: String): Option[Id] = str.toLongOption.map(tag)
  }
}
