import sbt._
import Keys._

object ApplicationBuild extends Build {
  val buildName         = "play-autosource"

  val BuildSettings = Defaults.defaultSettings ++ Seq(
    scalaVersion := "2.10.1",
    organization := "play-autosource",
    version := "0.11-SNAPSHOT",
    resolvers ++= mandubianRepo
  ) ++ Publish.settings

  val mandubianRepo = Seq(
    "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
    "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/"
  )

  lazy val main = Project(
    id = buildName,
    base = file("."),
    settings = BuildSettings ++ Seq(
      publish      := {}
    )
  ) aggregate(core, reactivemongo, datomisca, couchbase)

  lazy val core = Project(
    id = "core", 
    base = file("core"),
    settings = BuildSettings ++ Seq(
      libraryDependencies ++= Seq(
        "play-json-zipper"  %% "play-json-zipper"  % "0.1-SNAPSHOT"             ,
        "play"              %% "play-json"         % "2.2-SNAPSHOT"             ,
        "play"              %% "play"              % "2.1.1"        % "provided",
        "org.specs2"        %% "specs2"            % "1.13"         % "test"    ,
        "junit"              % "junit"             % "4.8"          % "test"
      )
    )
  )

  lazy val reactivemongo = Project(
    id = "reactivemongo",
    base = file("reactivemongo"),
    settings = BuildSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.reactivemongo" %% "play2-reactivemongo" % "0.9",
        "org.reactivemongo" %% "reactivemongo"       % "0.9"
      )
    )
  ) dependsOn(core)

  lazy val datomisca = Project(
    id = "datomisca",
    base = file("datomisca"),
    settings = BuildSettings ++ Seq(
      resolvers ++= Seq(
        "datomisca-repo snapshots" at "https://github.com/pellucidanalytics/datomisca-repo/raw/master/snapshots",
        "datomisca-repo releases" at "https://github.com/pellucidanalytics/datomisca-repo/raw/master/releases"
      ),
      libraryDependencies ++= Seq(
        "play.modules.datomisca" %% "play-datomisca" % "0.5.1",
        "com.datomic" % "datomic-free" % "0.8.4007" % "provided" exclude("org.slf4j", "slf4j-nop") 
      )
    )
  ) dependsOn(core)

  lazy val couchbase = Project(
    id = "couchbase",
    base = file("couchbase"),
    settings = BuildSettings ++ Seq(
      resolvers ++= Seq(
        "Ancelin Repository" at "https://raw.github.com/mathieuancelin/play2-couchbase/master/repository/snapshots",
        "Spy Repository" at "http://files.couchbase.com/maven2"
      ),
      libraryDependencies ++= Seq(
        "org.ancelin.play2.couchbase" %% "play2-couchbase" % "0.1-SNAPSHOT",
        "play"              %% "play"              % "2.1.1"        % "provided"
      )
    )
  ) dependsOn(core)

  object Publish {
    lazy val settings = Seq(
      publishMavenStyle := true,
      publishTo <<= version { (version: String) =>
        val localPublishRepo = "../mandubian-mvn/"
        if(version.trim.endsWith("SNAPSHOT"))
          Some(Resolver.file("snapshots", new File(localPublishRepo + "/snapshots")))
        else Some(Resolver.file("releases", new File(localPublishRepo + "/releases")))
      }
    )
  }
}
