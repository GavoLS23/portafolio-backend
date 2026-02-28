package com.portafolio.config

import cats.effect.IO
import cats.syntax.all.*
import ciris.*

/** Configuración de base de datos (HikariCP + Doobie). */
final case class DbConfig(
                           url: String,
                           user: String,
                           password: Secret[String],
                           schema: String,
                           poolSize: Int
                         )

/** Configuración del servidor JWT. */
final case class JwtConfig(
                            secret: Secret[String],
                            expirationHours: Long
                          )

/** Configuración de AWS / S3. */
final case class AwsConfig(
                            accessKeyId: Secret[String],
                            secretAccessKey: Secret[String],
                            region: String,
                            s3Bucket: String
                          )

/** Configuración del servidor HTTP. */
final case class ServerConfig(
                               host: String,
                               port: Int,
                               allowedOrigins: List[String]
                             )

/** Configuración del usuario administrador inicial. */
final case class AdminConfig(
                              email: String,
                              password: Secret[String]
                            )

/** Configuración global de la aplicación. */
final case class AppConfig(
                            db: DbConfig,
                            jwt: JwtConfig,
                            aws: AwsConfig,
                            server: ServerConfig,
                            admin: AdminConfig
                          )

object AppConfig:

  // ─────────────────────────────────────────────────────────────
  // Loaders individuales
  // ─────────────────────────────────────────────────────────────

  private val dbConfig: ConfigValue[Effect, DbConfig] =
    (
      env("DB_URL"),
      env("DB_USER"),
      env("DB_PASSWORD").secret,
      env("DB_SCHEMA").default("portafolio"),
      env("DB_POOL_SIZE").as[Int].default(10)
    ).mapN(DbConfig.apply)

  private val jwtConfig: ConfigValue[Effect, JwtConfig] =
    (
      env("JWT_SECRET").secret,
      env("JWT_EXPIRATION_HOURS").as[Long].default(24L)
    ).mapN(JwtConfig.apply)

  private val awsConfig: ConfigValue[Effect, AwsConfig] =
    (
      env("AWS_ACCESS_KEY_ID").secret,
      env("AWS_SECRET_ACCESS_KEY").secret,
      env("AWS_REGION"),
      env("AWS_S3_BUCKET")
    ).mapN(AwsConfig.apply)

  private val serverConfig: ConfigValue[Effect, ServerConfig] =
    (
      env("SERVER_HOST").default("0.0.0.0"),
      env("SERVER_PORT").as[Int].default(8080),
      env("ALLOWED_ORIGINS").default("http://localhost:4200")
    ).mapN { (host, port, origins) =>
      ServerConfig(
        host = host,
        port = port,
        allowedOrigins = origins.split(",").map(_.trim).toList
      )
    }

  private val adminConfig: ConfigValue[Effect, AdminConfig] =
    (
      env("ADMIN_EMAIL"),
      env("ADMIN_PASSWORD").secret
    ).mapN(AdminConfig.apply)

  // ─────────────────────────────────────────────────────────────
  // Loader principal
  // ─────────────────────────────────────────────────────────────

  /** Carga la configuración completa de la aplicación.
   * Falla en startup si falta alguna variable obligatoria.
   */
  def load: IO[AppConfig] =
    (
      dbConfig,
      jwtConfig,
      awsConfig,
      serverConfig,
      adminConfig
    ).parMapN(AppConfig.apply)
      .load[IO]