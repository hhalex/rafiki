package com.lion.rafiki

import com.lion.rafiki.domain.TaggedId
import com.lion.rafiki.auth.PasswordHasher
import cats.effect.MonadCancel

import doobie.util.meta.Meta

package object sql {
  type TaglessMonadCancel[F[_]] = MonadCancel[F, Throwable]
  def createMetaId[Id](t: TaggedId[_]): Meta[t.Id] =
    Meta[Long].imap(t.tag)(t.unTag)
  def createMetaPasswd(): Meta[PasswordHasher.Password] =
    Meta[String].imap(PasswordHasher.tag)(_.asInstanceOf[String])
}
