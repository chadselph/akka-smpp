name := "Akka SMPP"
 
version := "0.1"
 
scalaVersion := "2.10.2"
 
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
 
libraryDependencies +=
  "com.typesafe.akka" %% "akka-actor" % "2.3-M2"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0" % "test"

