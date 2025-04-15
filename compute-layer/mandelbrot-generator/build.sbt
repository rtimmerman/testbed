ThisBuild / scalaVersion := "3.5.0"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.rrt"
ThisBuild / organizationName := "rrt"

resolvers += "confluent" at "https://packages.confluent.io/maven/"

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.

libraryDependencies += "org.apache.kafka" % "kafka-clients" % "4.0.0"
libraryDependencies += "org.typelevel" %% "spire" % "0.18.0"
libraryDependencies += "org.scalanlp" %% "breeze" % "2.1.0"
libraryDependencies += "org.mongodb" % "mongodb-driver-sync" % "4.10.2"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.32"
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.32"
libraryDependencies += "org.apache.avro" % "avro" % "1.11.0"
libraryDependencies += "io.confluent" % "kafka-avro-serializer" % "7.0.0"
// https://mvnrepository.com/artifact/org.mockito/mockito-core
libraryDependencies += "org.mockito" % "mockito-core" % "5.17.0" % Test
libraryDependencies += "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.16.0"
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.0"
// https://mvnrepository.com/artifact/org.scalatest/scalatest
libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.16"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.16" % Test
libraryDependencies += "org.scalamock" %% "scalamock" % "7.3.0" % Test

libraryDependencies += "io.prometheus" % "simpleclient" % "0.16.0"
libraryDependencies += "io.prometheus" % "simpleclient_httpserver" % "0.16.0"
//libraryDependencies += "io.prometheus" % "prometheus-metrics-exporter-httpserver" % "1.3.3"

fork := true
run / javaOptions += "-Djavax.net.ssl.keyStore=bbk.ac.uk.p12"
run / javaOptions += "-Djavax.net.ssl.keyStorePassword=xiec.gate.r"

ThisBuild / assemblyMergeStrategy := { case _ =>
  MergeStrategy.first
}
