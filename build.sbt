name := "Akka SMPP"

organization := "akkasmpp"

version := "0.2-SNAPSHOT"

scalaVersion := "2.10.3"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.7"

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.3.7"

libraryDependencies += "com.cloudhopper" % "ch-commons-charset" % "3.0.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % "test"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.5" % "test"

net.virtualvoid.sbt.graph.Plugin.graphSettings

