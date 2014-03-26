import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "sample-angular"
  val appVersion      = "2.0-SNAPSHOT"

  val mandubianRepo = Seq(
    "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
    "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/"
  )

  val localIvy2Repo = Seq (
    Resolver.url("Local IVY2 Repository", url("file://"+Path.userHome.absolutePath+"/.ivy2/local"))(Resolver.ivyStylePatterns)
  )

  val sonatypeRepo = Seq(
    "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"
  )
  val appDependencies = Seq()

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers ++= localIvy2Repo ++ mandubianRepo ++ sonatypeRepo,
    libraryDependencies ++= Seq(
      "play-autosource"   %% "reactivemongo"       % "2.0-SNAPSHOT",
      "org.specs2"        %% "specs2"              % "1.13"        % "test",
      "junit"              % "junit"               % "4.8"         % "test"
    )
  )

}
