import sbt._
import Keys._

object ApplicationBuild extends Build {
  val buildName    = "play-autosource"

  // coreVersion is the version of Autosource specification
  // each implementation of the spec should have the same major version
  // but can evolve with its own minor version
  // For example:
  // coreVersion = 1.0 -> reactiveMongo v1.0, 1.1, 1.2, 1.3...
  val coreVersion  = "1.0"

  val mandubianRepo = Seq(
    "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
    "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/",
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
  )

  val BuildSettings = Defaults.defaultSettings ++ Seq(
    scalaVersion := "2.10.2",
    organization := "play-autosource",

    resolvers ++= mandubianRepo
  ) ++ Publish.settings

  lazy val main = Project(
    id = buildName,
    base = file("."),
    settings = BuildSettings ++ Seq(
      publish      := {}
    )
  ) aggregate(
    core,
    reactivemongo,
    datomisca,
    couchbase,
    slick
  )

  lazy val core = Project(
    id = "core",
    base = file("core"),
    settings = BuildSettings ++ Seq(
      version := coreVersion,

      libraryDependencies ++= Seq(
        "play-json-zipper"  %% "play-json-zipper"  % "1.0"                      ,
        "com.typesafe.play" %% "play-json"         % "2.2.0"                    ,
        "com.typesafe.play" %% "play"              % "2.2.0"        % "provided",
        "org.specs2"        %% "specs2"            % "1.13"         % "test"    ,
        "junit"              % "junit"             % "4.8"          % "test"
      )
    )
  )

  lazy val reactivemongo = Project(
    id = "reactivemongo",
    base = file("reactivemongo"),
    settings = BuildSettings ++ Seq(
      // can be customized by keeping major version of the core version
      version := "1.0-SNAPSHOT",

      resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
      libraryDependencies ++= Seq(
        "org.reactivemongo" %% "play2-reactivemongo" % "0.10.0-SNAPSHOT",
        "org.reactivemongo" %% "reactivemongo"       % "0.10.0-SNAPSHOT",
        "com.typesafe.play" %% "play"                % "2.2.0"        % "provided"
      )
    )
  ) dependsOn(core)

  lazy val datomisca = Project(
    id = "datomisca",
    base = file("datomisca"),
    settings = BuildSettings ++ Seq(
      // can be customized by keeping major version of the core version
      version := "1.0",

      resolvers ++= Seq(
        "datomisca-repo snapshots" at "https://github.com/pellucidanalytics/datomisca-repo/raw/master/snapshots",
        "datomisca-repo releases" at "https://github.com/pellucidanalytics/datomisca-repo/raw/master/releases",
        "datomisca-mvn-repo" at "http://dl.bintray.com/content/pellucid/maven",
        "clojars"   at "https://clojars.org/repo"
      ),
      libraryDependencies ++= Seq(
        "com.pellucid"      %% "datomisca"      % "0.5.2",
        "com.pellucid"      %% "play-datomisca" % "0.5.2",
        "com.datomic"        % "datomic-free"   % "0.8.4218" % "provided" exclude("org.slf4j", "slf4j-nop"),
        "com.typesafe.play" %% "play"           % "2.2.0"    % "provided"
      )
    )
  ) dependsOn(core)

  lazy val couchbase = Project(
    id = "couchbase",
    base = file("couchbase"),
    settings = BuildSettings ++ Seq(
      // can be customized by keeping major version of the core version
      version := "1.0-SNAPSHOT",

      resolvers ++= Seq(
        "Ancelin Repository" at "https://raw.github.com/mathieuancelin/play2-couchbase/master/repository/snapshots",
        "Spy Repository" at "http://files.couchbase.com/maven2"
      ),
      libraryDependencies ++= Seq(
        "org.ancelin.play2.couchbase" %% "play2-couchbase"   % "0.5-SNAPSHOT",
        "com.typesafe.play"           %% "play"              % "2.2.0"        % "provided",
        "com.typesafe.play"           %% "play-cache"        % "2.2.0"        % "provided"
      )
    )
  ) dependsOn(core)



  lazy val slick = Project(
    id = "slick",
    base = file("slick"),
    settings = BuildSettings ++ Seq(
      // can be customized by keeping major version of the core version
      version := "1.1-SNAPSHOT",

      resolvers ++= Seq(
        "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
        "Typesafe snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
        "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
      ),

      libraryDependencies ++= Seq(
        "com.typesafe.play"   %%  "play-slick"    % "0.5.0.4",
        "com.typesafe.play"   %%  "play"          % "2.2.0"        % "provided",
        "com.typesafe.play"   %%  "play-jdbc"     % "2.2.0"        % "provided"
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
