ThisBuild / scalaVersion := "3.3.0"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

resolvers += "confluent" at "https://packages.confluent.io/maven/"

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.

libraryDependencies += "org.apache.kafka" % "kafka-clients" % "3.5.1"
libraryDependencies += "org.typelevel" %% "spire" % "0.18.0"
libraryDependencies += "org.scalanlp" %% "breeze" % "2.1.0"
libraryDependencies += "org.mongodb" % "mongodb-driver-sync" % "4.10.2"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.32"
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.32"
libraryDependencies += "org.apache.avro" % "avro" % "1.11.0"
libraryDependencies += "io.confluent" % "kafka-avro-serializer" % "7.0.0"
libraryDependencies += "org.testifyproject.mock" % "mockito" % "1.0.6"
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2"


fork := true
run / javaOptions += "-Djavax.net.ssl.keyStore=/home/roderick/workspace/mongo-cluster/compute-layer/mandelbrot-generator/bbk.ac.uk.p12"
run / javaOptions += "-Djavax.net.ssl.keyStorePassword=xiec.gate.r"

ThisBuild / assemblyMergeStrategy := { case _ =>
  MergeStrategy.first
}
