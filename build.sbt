import sbt.ExclusionRule

val Http4sVersion = "1.0.0-M23"
val CatsVersion = "2.6.1"
val CirceVersion = "0.14.1"
val DoobieVersion = "1.0.0-M5"
val Specs2Version = "4.11.0"
val LogbackVersion = "1.2.3"

lazy val root = (project in file("."))
  .settings(
    organization := "lion",
    name := "rafiki",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "3.0.0",
    crossScalaVersions ++= Seq("2.13.5", "3.0.0"),
    libraryDependencies ++= Seq(
      "com.github.nscala-time" %% "nscala-time"      % "2.28.0",
      "org.http4s"          %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"          %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"          %% "http4s-circe"        % Http4sVersion,
      "org.http4s"          %% "http4s-dsl"          % Http4sVersion,
      "org.typelevel"       %% "cats-core"           % CatsVersion,
      ("com.github.t3hnar"  %% "scala-bcrypt"        % "4.3.0").cross(CrossVersion.for3Use2_13),
      "io.circe"            %% "circe-generic"       % CirceVersion,
      "io.circe"            %% "circe-core"       % CirceVersion,
      "io.circe"            %% "circe-parser"       % CirceVersion,
      "org.tpolecat"        %% "doobie-core"         % DoobieVersion,
      "org.tpolecat"        %% "doobie-postgres"     % DoobieVersion,
      ("org.tpolecat"        %% "doobie-specs2"       % DoobieVersion % "test").cross(CrossVersion.for3Use2_13) excludeAll(
        ExclusionRule(organization = "org.typelevel"),
        ExclusionRule(organization = "org.tpolecat"),
        ExclusionRule(organization = "co.fs2"),
        ExclusionRule(organization = "org.scala-lang.modules")
      ), // Specs2 support for typechecking statements.
      ("org.specs2"          %% "specs2-core"         % Specs2Version % "test").cross(CrossVersion.for3Use2_13),
      ("org.specs2"          %% "specs2-mock"         % Specs2Version % "test").cross(CrossVersion.for3Use2_13),
      "org.typelevel" %% "cats-effect-testing-specs2" % "1.1.1" % Test,
      "ch.qos.logback"      %  "logback-classic"     % LogbackVersion
    ),
    watchSources ++= (baseDirectory.value / "public/ui" ** "*").get,
    Compile / resourceDirectory := file("ui") / "build",
    Test / envVars := Map( "DATABASE_URL" -> "postgres://postgres:password@localhost:5432/postgres", "DEV" -> "1", "HOT_USERS" -> "admin:pass")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-Ykind-projector",
  "-Xsemanticdb",
  "-new-syntax",
  //"-Ysafe-init",
  "-unchecked",
  "-Xmigration",
  "-rewrite"
)
enablePlugins(JavaServerAppPackaging)
