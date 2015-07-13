name := "pizza-sov-relay"

version := "1.0"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "Sonatype snapshots repository" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Spray repository" at "http://repo.spray.io/",
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies += "io.backchat.hookup" % "hookup_2.10" % "0.4.0"
libraryDependencies += "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"

mainClass := Some("SovRelay")