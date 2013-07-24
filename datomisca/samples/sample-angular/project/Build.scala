import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "sample-angular"
  val appVersion      = "1.0-SNAPSHOT"

  val mandubianRepo = Seq(
    "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
    "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/",
    Resolver.url("Local IVY2 Repository", url("file://"+Path.userHome.absolutePath+"/.ivy2/local"))(Resolver.ivyStylePatterns)
  )

  val datomicRepo = Seq(
    "datomisca-repo snapshots" at "https://github.com/pellucidanalytics/datomisca-repo/raw/master/snapshots",
    "datomisca-repo releases"  at "https://github.com/pellucidanalytics/datomisca-repo/raw/master/releases",
    // to get Datomic free (for pro, you must put in your own repo or local)
    "clojars" at "https://clojars.org/repo"
  )

  val appDependencies = Seq()

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers ++= mandubianRepo ++ datomicRepo,
    libraryDependencies ++= Seq(
      "play-autosource"   %% "datomisca"           % "0.11-SNAPSHOT",
      "com.datomic"        % "datomic-free"        % "0.8.4020.26",
      "org.specs2"        %% "specs2"              % "1.13"        % "test",
      "junit"              % "junit"               % "4.8"         % "test"
    )
  )

}
