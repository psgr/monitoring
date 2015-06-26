import com.typesafe.sbt.SbtNativePackager.packageArchetype

organization := "me.passenger"

name := "monitoring"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.6"

sbtVersion := "0.13.8"

libraryDependencies ++= {
  val akkaV = "2.3.11"
  val akkaHttpV = "1.0-RC4"
  Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "com.typesafe" % "config" % "1.2.1",
    "com.typesafe.play" %% "play-json" % "2.4.1",
    "ch.qos.logback" % "logback-classic" % "1.1.2",
    "net.logstash.logback" % "logstash-logback-encoder" % "2.8",
    "org.specs2" %% "specs2" % "2.3.12" % "test")
}

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

mainClass := Some("psgr.monitoring.MonitoringApp")

packageArchetype.java_application