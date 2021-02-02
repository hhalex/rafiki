package com.lion.rafiki

import cats.effect.Sync

import java.net.URI
import scala.util.Try

case class Conf(dbUser: String, dbPassword: String, dbUrl: String, host: String, port: Int, devMode: Boolean)

object Conf {
  def apply[F[_]](env: java.util.Map[String, String])(implicit F: Sync[F]): F[Conf] = {
    val devMode = env.get("DEV") != null
    val port = env.getOrDefault("PORT", "8080").toInt
    Try(new URI(env.get("DATABASE_URL"))).fold(
      F.raiseError,
      dbUri => {
        val username = dbUri.getUserInfo.split(":")(0)
        val password = dbUri.getUserInfo.split(":")(1)
        val dbUrl = "jdbc:postgresql://" + dbUri.getHost + ':' + dbUri.getPort + dbUri.getPath + (if (devMode) "" else "?sslmode=require")
        F.pure(Conf(username, password, dbUrl, "0.0.0.0", port, devMode))
      }
    )
  }
}
