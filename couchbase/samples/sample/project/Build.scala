import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "sample"
  val appVersion      = "1.0-SNAPSHOT"

  val autosourceRepos = Seq(
    "ReactiveCouchbase Snapshots" at "https://raw.github.com/ReactiveCouchbase/repository/master/snapshots/",
    "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
    "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/"
  )

  val appDependencies = Seq()

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers ++= autosourceRepos,
    libraryDependencies ++= Seq(
      "play-autosource" %% "couchbase" % "2.0-SNAPSHOT"
    )
  )
}
