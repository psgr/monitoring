import com.typesafe.sbt.SbtNativePackager.packageArchetype

organization := "com.carryx"

name := "monitoring"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.2"

sbtVersion := "0.13.5"

libraryDependencies ++= {
  val akkaV = "2.3.5"
  val sprayV = "1.3.1"
  Seq(
    "io.spray" %% "spray-can" % sprayV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "com.typesafe" % "config" % "1.2.1",
    "com.typesafe.play" %% "play-json" % "2.3.0",
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

mainClass := Some("carryx.monitoring.MonitoringApp")

packageArchetype.java_application