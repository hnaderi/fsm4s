import sbt.ThisBuild
import Dependencies.Libraries._

name := "fsm4s"

val libVersion = "0.1.0"

version := libVersion

lazy val scala2_13 = "2.13.2"
ThisBuild / scalaVersion := scala2_13
ThisBuild / version := libVersion


val common = Project("fsm4s-core", file("common"))
  .settings(
  	Common.settings,
    libraryDependencies ++=
      odin ++ circe ++ postgres ++ doobie
  )

val docs = Project("fsm4s-docs", file("docs-build"))
  .dependsOn(common)
  .enablePlugins(MdocPlugin)

mdocVariables := Map(
  "VERSION" -> version.value
)

val root = (project in file("."))
  .settings(
    Common.settings,
    version := libVersion
  )
  .aggregate(common, docs)
  .enablePlugins(MicrositesPlugin)
