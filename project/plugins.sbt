resolvers ++= Seq(
  "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  Resolver.url("bintray-sbt-plugin-releases",
    url("http://dl.bintray.com/content/sbt/sbt-plugin-releases")
  )(Resolver.ivyStylePatterns)
)

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0-SNAPSHOT")

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")
