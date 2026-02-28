// ─────────────────────────────────────────────────────────────
// Global build configuration (REQUIRED for Scalafix)
// ─────────────────────────────────────────────────────────────
inThisBuild(
  List(
    organization := "com.portafolio",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "3.3.5",

    // ── SemanticDB (required for Scalafix semantic rules)
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,

    // ── Required for RemoveUnused (Scala 3)
    scalacOptions ++= Seq(
      "-Wunused:all"
    )
  )
)

// ─────────────────────────────────────────────────────────────
// Versions
// ─────────────────────────────────────────────────────────────
lazy val tapirVersion = "1.9.10"
lazy val doobieVersion = "1.0.0-RC4"
lazy val http4sVersion = "0.23.24"
lazy val circeVersion = "0.14.6"
lazy val catsEffectVersion = "3.5.3"
lazy val awsSdkVersion = "2.23.15"
lazy val log4catsVersion = "2.6.0"

// ─────────────────────────────────────────────────────────────
// Project
// ─────────────────────────────────────────────────────────────
lazy val root = (project in file("."))
  .settings(
    name := "portafolio-backend",

    // ── Dependencies ────────────────────────────────────────
    libraryDependencies ++= Seq(

      // Cats Effect
      "org.typelevel" %% "cats-effect" % catsEffectVersion,

      // Http4s
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,

      // Tapir
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-refined" % tapirVersion,

      // Doobie
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,

      // Circe
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-refined" % circeVersion,

      // Ciris
      "is.cir" %% "ciris" % "3.6.0",
      "is.cir" %% "ciris-refined" % "3.6.0",

      // Flyway
      "org.flywaydb" % "flyway-core" % "10.4.1",
      "org.flywaydb" % "flyway-database-postgresql" % "10.4.1",

      // Refined
      "eu.timepit" %% "refined" % "0.11.0",

      // JWT
      "com.github.jwt-scala" %% "jwt-circe" % "9.4.4",

      // BCrypt
      "org.mindrot" % "jbcrypt" % "0.4",

      // AWS SDK
      "software.amazon.awssdk" % "s3" % awsSdkVersion,
      "software.amazon.awssdk" % "s3-transfer-manager" % awsSdkVersion,

      // Logging
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.14",

      // Testing
      "org.scalatest" %% "scalatest" % "3.2.18" % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
      "org.tpolecat" %% "doobie-scalatest" % doobieVersion % Test
    ),

    // ── JVM / Runtime ───────────────────────────────────────
    Compile / run / fork := true,

    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked"
    ),

    // ── Assembly ────────────────────────────────────────────
    assembly / mainClass := Some("com.portafolio.Main"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*) => MergeStrategy.first
      case "reference.conf" => MergeStrategy.concat
      case "module-info.class" => MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  )
