import sbt._

object Dependencies {
  object Doobie {
    private val version = "0.13.4"

    val core = "org.tpolecat" %% "doobie-core" % version
    val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % version
    val all = Seq(core, doobiePostgres)
  }

  object Postgres {
    private val version = "42.2.24"

    val driver = "org.postgresql" % "postgresql" % version

    val all = Seq(driver)
  }

  object Tofu {
    private val version = "0.10.6"
    val tofu = "tf.tofu" %% "tofu" % version
    val tofuLogging = "tf.tofu" %% "tofu-logging" % version
    val tofuDoobie = "tf.tofu" %% "tofu-doobie-logging" % version

    val all = Seq(tofu, tofuLogging, tofuDoobie)
  }

  object Derevo {
    private val version = "0.12.6"

    val circe = "tf.tofu" %% "derevo-circe" % version

    val all = Seq(circe)
  }

  object Tapir {
    private val version = "0.18.3"

    val core = "com.softwaremill.sttp.tapir" %% "tapir-core" % version
    val jsonCirce =
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % version
    val tapirHttp4s =
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % version
    val derevo = "com.softwaremill.sttp.tapir" %% "tapir-derevo" % version
    val openapiDocs =
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % version
    val openapiCirceYaml =
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % version
    val tapirSwagger =
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s" % version

    val all = Seq(
      core,
      jsonCirce,
      tapirHttp4s,
      derevo,
      openapiDocs,
      openapiCirceYaml,
      tapirSwagger
    )
  }

  object FS2 {
    private val version = "2.5.9"

    val core = "co.fs2" %% "fs2-core" % version
    val io = "co.fs2" %% "fs2-io" % version

    val all = Seq(core, io)
  }

  object Izumi {
    private val version = "1.0.0"

    val distage = "io.7mind.izumi" %% "distage-core" % version

    val all = Seq(distage)
  }

  object Pureconfig {
    private val version = "0.13.0"

    val pureconfig = "com.github.pureconfig" %% "pureconfig" % version
    val catsEffectInterop =
      "com.github.pureconfig" %% "pureconfig-cats-effect" % version

    val all = Seq(pureconfig, catsEffectInterop)
  }

  object Http4s {
    private val version = "0.22.0"

    val dsl = "org.http4s" %% "http4s-dsl" % version

    val all = Seq(dsl)
  }

  object Flyway {
    val version = "7.14.1"
    val flywayCore = "org.flywaydb" % "flyway-core" % version
    val all = Seq(flywayCore)
  }

  val all: Seq[ModuleID] =
    Doobie.all ++ Postgres.all ++ Tofu.all ++ Tapir.all ++ Derevo.all ++
      Izumi.all ++ Pureconfig.all ++ Http4s.all ++ Flyway.all
}
