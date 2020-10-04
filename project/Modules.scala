import sbt._
import sbt.Keys._

import Dependencies._

object Modules {

  /**
    * Note that this function is used in Protocols sub project
    * and is addressing from there!
    */
  def protocol(module: String): Project = {
    val id = s"protocols-$module"
    Project(id, file(s"$module"))
      .settings(
        Common.settings,
        libraryDependencies ++= (
          Libraries.refined ++
            Libraries.newType
        )
      )
      .dependsOn(Project("core", file("core")))
  }

  def broker(module: String): Project = {
    val id = s"broker-$module"
    Project(id, file(s"services/broker-$module"))
      .settings(
        Common.settings
      )
  }

  def service(name: String): Project = {
    val id = s"$name"
    Project(id, file(s"services/$name"))
      .settings(
        Common.settings
      )
  }

}
