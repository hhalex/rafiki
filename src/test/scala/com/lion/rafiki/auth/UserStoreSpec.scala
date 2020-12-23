package com.lion.rafiki.auth

import cats.effect.IO
import com.lion.rafiki.auth.UserStore.{UserId, tagFUUIDAsUserId}
import io.chrisdavenport.fuuid.FUUID
import org.specs2.Specification

class UserStoreSpec extends Specification { def is = s2"""
      User Store:
        retrieves existing users ${buildRefStore.unsafeRunSync()}
    """

  val user = UsernamePasswordCredentials("username", "password")
  val buildRefStore = for {
    userStore <- UserStore(user)
    retrievedUser <- userStore.checkPassword(user)
  } yield retrievedUser.map(_.username) must beSome(user.username)
}

object UserStoreSpec {
  def randomUserId: IO[UserId] = FUUID.randomFUUID[IO].map(tagFUUIDAsUserId)
}