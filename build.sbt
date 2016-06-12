name := "Akka SMPP"

organization := "akkasmpp"

version := "0.3.0-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"


val akkaV = "2.4.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.10",
  "com.cloudhopper" % "ch-commons-charset" % "3.0.2",
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.5" % "test"
)

javacOptions in Compile ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation")


net.virtualvoid.sbt.graph.Plugin.graphSettings

