val Http4sVersion = "1.0.0-M21"
val TsecVersion = "0.2.1"
val CirceVersion = "0.14.0-M5"
val DoobieVersion = "1.0.0-M2"
val Specs2Version = "4.10.5"
val LogbackVersion = "1.2.3"

lazy val root = (project in file("."))
  .settings(
    organization := "lion",
    name := "rafiki",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.5",
    libraryDependencies ++= Seq(
      "org.http4s"          %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"          %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"          %% "http4s-circe"        % Http4sVersion,
      "org.http4s"          %% "http4s-dsl"          % Http4sVersion,
      "com.github.t3hnar"   %% "scala-bcrypt"        % "4.3.0",
      "commons-codec"       %% "commons-codec"       % "1.15",
      //"io.github.jmcardon"  %% "tsec-common"         % TsecVersion exclude("co.fs2", "fs2-io_2.13"),
      //"io.github.jmcardon"  %% "tsec-password"       % TsecVersion exclude("co.fs2", "fs2-io_2.13"),
      "io.circe"            %% "circe-generic"       % CirceVersion,
      "org.tpolecat"        %% "doobie-core"         % DoobieVersion,
      "org.tpolecat"        %% "doobie-postgres"     % DoobieVersion,
      "org.tpolecat"        %% "doobie-specs2"       % DoobieVersion % "test", // Specs2 support for typechecking statements.
      "org.specs2"          %% "specs2-core"         % Specs2Version % "test",
      "ch.qos.logback"      %  "logback-classic"     % LogbackVersion
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.3" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    watchSources ++= (baseDirectory.value / "public/ui" ** "*").get,
    Compile / resourceDirectory := file("ui") / "build"
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Xfatal-warnings",
  "-Xlog-implicits"
)

enablePlugins(JavaServerAppPackaging)
