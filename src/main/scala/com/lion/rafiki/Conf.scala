package com.lion.rafiki

import cats.effect.Sync
import com.lion.rafiki.auth.UserCredentials

import java.net.URI
import scala.util.Try

case class Conf(dbUser: String, dbPassword: String, dbUrl: String, host: String, port: Int, hotUsersList: Seq[UserCredentials], devMode: Boolean)

object Conf {
  def apply[F[_]]()(implicit F: Sync[F]): F[Conf] = {
    val env = System.getenv()
    val devMode = env.get("DEV") != null
    val port = env.getOrDefault("PORT", "8080").toInt
    // Hot users are stored like this: "hotuser1:password1;hotuser2:password2"
    val hotUsersList = env.getOrDefault("HOT_USERS", "").split(";").toSeq.map(_.split(":")).collect({
      case Array(username, password) => UserCredentials(username, password)
    })

    Try(new URI(env.get("DATABASE_URL"))).fold(
      F.raiseError,
      dbUri => {
        val username = dbUri.getUserInfo.split(":")(0)
        val password = dbUri.getUserInfo.split(":")(1)
        val dbUrl = "jdbc:postgresql://" + dbUri.getHost + ':' + dbUri.getPort + dbUri.getPath + (if (devMode) "" else "?sslmode=require")
        F.pure(Conf(username, password, dbUrl, "0.0.0.0", port, hotUsersList, devMode))
      }
    )
  }
}
