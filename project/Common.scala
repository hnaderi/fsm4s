import Dependencies.Libraries.{betterMonadicFor, kindProjector}
import sbt.Keys.{
  libraryDependencies,
  organization,
  organizationName,
  scalacOptions
}
import wartremover.WartRemover.autoImport.{Wart, Warts, wartremoverErrors}

object Common {
  private val commonScalacOptions = Seq(
    "-target:jvm-1.11",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
//    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Ymacro-annotations",
    "-Ywarn-unused",
    "-Ywarn-macros:after"
  )

  val settings = Seq(
    organization := "ir.hnaderi",
    scalacOptions ++= commonScalacOptions,
    wartremoverErrors ++= Warts
      .allBut(Wart.Nothing, Wart.Any, Wart.Overloading),
    libraryDependencies ++= Seq(kindProjector, betterMonadicFor)
  )
}
