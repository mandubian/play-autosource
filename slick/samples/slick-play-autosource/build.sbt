name := "slick-play-autosource"

version := "1.1-SNAPSHOT"

scalaVersion := "2.11.2"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/"

resolvers += "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/"

val localIvy2Repo = Resolver.url("Local IVY2 Repository", url("file://"+Path.userHome.absolutePath+"/.ivy2/local"))(Resolver.ivyStylePatterns)

resolvers += localIvy2Repo

libraryDependencies ++= Seq(
  jdbc,
  "play-autosource"     %%  "slick"               % "2.1-SNAPSHOT" changing(),
  "com.h2database"      %   "h2"                  % "1.3.173"
)
