lazy val commonSettings = Seq(
  scalaVersion := "2.12.5",
  organization := "org.vivaconagua",
  version      := "0.4.6-play27"
)

val silhouetteVersion = "5.0.2"


lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "play2-oauth-client",
    libraryDependencies ++= Seq(
      "com.mohiva" %% "play-silhouette" % silhouetteVersion,
      "com.mohiva" %% "play-silhouette-password-bcrypt" % silhouetteVersion,
      "com.mohiva" %% "play-silhouette-crypto-jca" % silhouetteVersion,
      "com.mohiva" %% "play-silhouette-persistence" % silhouetteVersion,
      "com.github.tyagihas" % "scala_nats_2.11" % "0.3.0",
      "com.typesafe.play" %% "play-guice" % "2.6.13",
//      "com.google.inject" % "guice" % "4.2.0",
//      "com.google.inject.extensions" % "guice-assistedinject" % "4.2.0",
//      "com.sandinh" %% "akka-guice" % "3.2.0",
      "com.iheart" %% "ficus" % "1.4.3",
      "net.codingwell" %% "scala-guice" % "4.1.1",
//      "org.apache.commons" % "commons-lang3" % "3.8.1", // has to be maintained by all play applications using this lib
      "com.typesafe.play" %% "play-json" % "2.7.1"
    ),
    scalacOptions ++= Seq(
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-unchecked", // Enable additional warnings where generated code depends on assumptions.
      "-Xfatal-warnings", // Fail the compilation if there are any warnings.
      "-Xlint", // Enable recommended additional warnings.
      "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
      "-Ywarn-dead-code", // Warn when dead code is identified.
      "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
      "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
      "-Ywarn-numeric-widen" // Warn when numerics are widened.
    )
  )

licenses := Seq("GPL v3" -> url("https://www.gnu.org/licenses/gpl-3.0.en.html"))

homepage := Some(url("https://dev.vivaconagua.org"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/Viva-con-Agua/play2-oauth-client"),
    "scm:git@github.com:Viva-con-Agua/play2-oauth-client.git"
  )
)

developers := List(
  Developer(
    id    = "johannsell",
    name  = "Johann Sell",
    email = "j.sell@vivaconagua.org",
    url   = url("https://cses.informatik.hu-berlin.de/members/johann.sell/")
  )
)

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  val profileM = sonatypeStagingRepositoryProfile.?.value
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else {
//    val staged = profileM map { stagingRepoProfile =>
//      "releases" at nexus +
//        "service/local/staging/deployByRepositoryId/" +
//        stagingRepoProfile.repositoryId
//    }
//
//    staged.orElse(Some("releases" at nexus + "service/local/staging/deploy/maven2"))
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}
