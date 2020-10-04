import sbt.{CrossVersion, compilerPlugin, _}

object Dependencies {

  object Versions {
    val cats = "2.1.1"
    val catsTagless = "0.11"
    val fs2 = "2.3.0"
    val catsEffect = "2.1.3"
    val monix = "3.2.1"
    val http4s = "0.21.4"
    val sttp = "2.1.5"
    val circe = "0.13.0"
    val doobie = "0.9.0"
    val refined = "0.9.14"
    val monocle = "2.0.3"
    val pureconfig = "0.12.3"
    val enumeratum = "1.6.1"
    val newType = "0.4.4"
    val tsec = "0.2.0"
    val fs2Rabbit = "2.1.1"
    val monovore = "1.0.0"
    val odin = "0.7.0"
    val betterMonadicFor = "0.3.1"
    val scalaCheck = "1.14.3"
    val scalaTestPlusScalaCheck = "3.1.0.0-RC2"
    val scalaTest = "3.1.2"
    val kindProjector = "0.11.0"
  }

  object Libraries {
    val cats: Seq[ModuleID] = Seq(
      "org.typelevel" %% "cats-core",
      "org.typelevel" %% "cats-free"
    ).map(_ % Versions.cats)

    val catsEffect: Seq[ModuleID] = Seq(
      "org.typelevel" %% "cats-effect" % Versions.catsEffect
    )
    val monix: Seq[ModuleID] = Seq("io.monix" %% "monix" % Versions.monix)

    val tagless: Seq[ModuleID] = Seq(
      "org.typelevel" %% "cats-tagless-macros" % Versions.catsTagless
    )

    val fs2: Seq[ModuleID] = Seq(
      "co.fs2" %% "fs2-core" % Versions.fs2
    )

    val circe: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras",
      "io.circe" %% "circe-refined"
    ).map(_ % Versions.circe)

    val monocle: Seq[ModuleID] = Seq(
      "com.github.julien-truffaut" %% "monocle-core",
      "com.github.julien-truffaut" %% "monocle-macro"
    ).map(_ % Versions.monocle)

    val rabbit: Seq[ModuleID] = Seq(
      "dev.profunktor" %% "fs2-rabbit",
      "dev.profunktor" %% "fs2-rabbit-circe"
    ).map(_ % Versions.fs2Rabbit)

    val doobie: Seq[ModuleID] = Seq(
      "org.tpolecat" %% "doobie-core",
      "org.tpolecat" %% "doobie-refined",
      "org.tpolecat" %% "doobie-h2",
      "org.tpolecat" %% "doobie-hikari"
    ).map(_ % Versions.doobie)

    val postgres: Seq[ModuleID] = Seq(
      "org.tpolecat" %% "doobie-postgres" % Versions.doobie
    )

    val http4s: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-dsl",
      "org.http4s" %% "http4s-blaze-server",
      "org.http4s" %% "http4s-blaze-client",
      "org.http4s" %% "http4s-circe",
      "org.http4s" %% "http4s-async-http-client"
    ).map(_ % Versions.http4s)

    val tsec: Seq[ModuleID] = Seq(
      "io.github.jmcardon" %% "tsec-mac" % Versions.tsec
    )

    val sttp: Seq[ModuleID] = Seq(
      //    "com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % Versions.sttp
      "com.softwaremill.sttp.client" %% "httpclient-backend-fs2" % Versions.sttp
    )

    val pureConfig: Seq[ModuleID] = Seq(
      "com.github.pureconfig" %% "pureconfig" % Versions.pureconfig
    )

    val refined: Seq[ModuleID] = Seq(
      "eu.timepit" %% "refined",
      "eu.timepit" %% "refined-cats",
      "eu.timepit" %% "refined-pureconfig"
    ).map(_ % Versions.refined)

    val enumeratum: Seq[ModuleID] = Seq(
      "com.beachape" %% "enumeratum" % Versions.enumeratum,
      "com.beachape" %% "enumeratum-circe" % Versions.enumeratum
    )

    val kindProjector: ModuleID = compilerPlugin(
      "org.typelevel" %% "kind-projector" % Versions.kindProjector cross CrossVersion.full
    )

    val betterMonadicFor: ModuleID = compilerPlugin(
      "com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor
    )
    val newType: Seq[ModuleID] = Seq(
      "io.estatico" %% "newtype" % Versions.newType
    )

    val scalaCheck: Seq[ModuleID] = Seq(
      "org.scalacheck" %% "scalacheck" % Versions.scalaCheck % Test,
      "eu.timepit" %% "refined-scalacheck" % Versions.refined % Test,
      "org.scalatestplus" %% "scalatestplus-scalacheck" % Versions.scalaTestPlusScalaCheck % Test
    )
    val testLib: Seq[ModuleID] = Seq(
      "org.scalatest" %% "scalatest" % Versions.scalaTest % Test
    )

    //  val loggingLib: Seq[ModuleID] = Seq(
//   "io.chrisdavenport" %% "log4cats-slf4j" % "1.1.1"
    //    "ch.qos.logback" % "logback-classic" % "1.2.3"
    //  )

    val odin: Seq[ModuleID] = Seq(
      "com.github.valskalla" %% "odin-core",
      //    "com.github.valskalla" %% "odin-json", //to enable JSON formatter if needed
      //    "com.github.valskalla" %% "odin-extras" ,//to enable additional features if needed (see docs)
      "com.github.valskalla" %% "odin-slf4j"
    ).map(_ % Versions.odin)

    val monovore: Seq[ModuleID] = Seq(
      "com.monovore" %% "decline" % Versions.monovore
    )
  }
}
