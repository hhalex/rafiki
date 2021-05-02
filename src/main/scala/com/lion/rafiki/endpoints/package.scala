package com.lion.rafiki

package object endpoints {
  class IdVar[A](F: Long => A) {
    def unapply(str: String) = str.toLongOption.map(F)
  }
}
