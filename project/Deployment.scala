import sbt._
import sbt.Keys._
import com.typesafe.sbt.packager.Keys._

object Deployment {
  private val Docker: Configuration = config("docker")
  val docker = Seq(
    dockerBaseImage := "openjdk:11-jre-slim",
    dockerExposedPorts := Seq(),
    dockerExposedVolumes := Seq("/opt/docker/logs"),
    daemonUserUid in Docker := Some("1001"),
    daemonUser in Docker := "broker",
    maintainer in Docker := "Hossein Naderi"
  )
}
