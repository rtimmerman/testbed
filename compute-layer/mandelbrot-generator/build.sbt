import Dependencies._

ThisBuild / scalaVersion := "2.13.5"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "mandelbrot-generator",
    libraryDependencies += scalaTest % Test
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.

libraryDependencies += "org.apache.kafka" %% "kafka" % "2.8.0"
libraryDependencies += "org.typelevel" %% "spire" % "0.17.0"
libraryDependencies += "org.scalanlp" %% "breeze" % "1.2"
libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "4.3.1"

fork := true
// run / javaOptions += "-Djavax.net.debug=all"
run / javaOptions += "-Djavax.net.ssl.keyStore=/home/roderick/workspace/mongo-cluster/compute-layer/mandelbrot-generator/bbk.ac.uk.p12"
run / javaOptions += "-Djavax.net.ssl.keyStorePassword=xiec.gate.r"
