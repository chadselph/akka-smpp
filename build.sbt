name := "Akka SMPP"

organization := "akkasmpp"

version := "0.2-SNAPSHOT"

scalaVersion := "2.10.3"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0-RC4",
  "com.typesafe.akka" %% "akka-http-experimental" % "1.0-RC4",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.10",
  "com.cloudhopper" % "ch-commons-charset" % "3.0.2",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.5" % "test"
)


net.virtualvoid.sbt.graph.Plugin.graphSettings

