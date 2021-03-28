val Http4sVersion = "0.21.8"
val TsecVersion = "0.2.1"
val FuuidVersion = "0.4.0"
val CirceVersion = "0.13.0"
val DoobieVersion = "0.9.0"
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
      "io.github.jmcardon"  %% "tsec-common"         % TsecVersion,
      "io.github.jmcardon"  %% "tsec-password"       % TsecVersion,
      "io.github.jmcardon"  %% "tsec-http4s"         % TsecVersion,
      "io.chrisdavenport"   %% "fuuid"               % FuuidVersion,
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
    resourceDirectory in Compile := file("ui") / "build"
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
