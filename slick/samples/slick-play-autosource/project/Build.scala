import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "slick-play-autosource"
  val appVersion      = "1.0-SNAPSHOT"

  val mandubianRepo = Seq(
    "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
    "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/"
  )

  val localIvy2Repo = Seq (
    Resolver.url("Local IVY2 Repository", url("file://"+Path.userHome.absolutePath+"/.ivy2/local"))(Resolver.ivyStylePatterns)
  )

  val appDependencies = Seq(
    jdbc      // The JDBC connection pool and the play.api.db API
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers ++= mandubianRepo ++ localIvy2Repo,
    libraryDependencies ++= Seq(

      // NOTE: there is no repo for slick-autosource yet, 
      // you should build it locally and have it on your local repo
      "play-autosource"     %%  "slick"               % "0.11-SNAPSHOT",
      "com.h2database"      %   "h2"                  % "1.3.166"
    )
  )

}
