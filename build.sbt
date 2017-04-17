name := "akka-smpp"

organization := "me.chadrs"

scalaVersion := "2.12.1"

crossScalaVersions := Seq("2.11.9", "2.12.1")

releaseCrossBuild := true

val akkaV = "2.5.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.cloudhopper" % "ch-commons-charset" % "3.0.2",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
)

javacOptions in Compile ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation")

licenses += ("Apache-2.0" -> url("https://opensource.org/licenses/Apache-2.0"))

