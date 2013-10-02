import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "sample"
  val appVersion      = "1.0-SNAPSHOT"

  val mandubianRepo = Seq(
    "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
    "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/"
  )

  val appDependencies = Seq()

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers ++= mandubianRepo,
    libraryDependencies ++= Seq(
      "play-autosource"   %% "couchbase"       % "1.0-SNAPSHOT" exclude("org.scala-stm", "scala-stm_2.10.0")
    )
  )
}
