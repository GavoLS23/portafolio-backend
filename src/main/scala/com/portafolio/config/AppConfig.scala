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

/** Configuración global de la aplicación leída desde variables de entorno.
  *
  * Uso: {{{ AppConfig.load.flatMap { config => ... } }}}
  */
final case class AppConfig(
    db: DbConfig,
    jwt: JwtConfig,
    aws: AwsConfig,
    server: ServerConfig,
    admin: AdminConfig
)

object AppConfig:

  /** Carga la configuración desde variables de entorno usando Ciris. Falla en startup si alguna variable obligatoria no está definida.
    */
  def load: IO[AppConfig] =
    (
      // ── DB ────────────────────────────────────────────────────────────
      env("DB_URL"),
      env("DB_USER"),
      env("DB_PASSWORD").secret,
      env("DB_SCHEMA").default("portafolio"),
      env("DB_POOL_SIZE").as[Int].default(10),

      // ── JWT ───────────────────────────────────────────────────────────
      env("JWT_SECRET").secret,
      env("JWT_EXPIRATION_HOURS").as[Long].default(24L),

      // ── AWS ───────────────────────────────────────────────────────────
      env("AWS_ACCESS_KEY_ID").secret,
      env("AWS_SECRET_ACCESS_KEY").secret,
      env("AWS_REGION"),
      env("AWS_S3_BUCKET"),

      // ── Server ────────────────────────────────────────────────────────
      env("SERVER_HOST").default("0.0.0.0"),
      env("SERVER_PORT").as[Int].default(8080),
      env("ALLOWED_ORIGINS").default("http://localhost:4200"),

      // ── Admin ─────────────────────────────────────────────────────────
      env("ADMIN_EMAIL"),
      env("ADMIN_PASSWORD").secret
    ).parMapN {
      (
          dbUrl,
          dbUser,
          dbPass,
          dbSchema,
          poolSize,
          jwtSecret,
          jwtExpH,
          awsKey,
          awsSecret,
          awsRegion,
          s3Bucket,
          serverHost,
          serverPort,
          origins,
          adminEmail,
          adminPass
      ) =>
        AppConfig(
          db = DbConfig(
            url = dbUrl,
            user = dbUser,
            password = dbPass,
            schema = dbSchema,
            poolSize = poolSize
          ),
          jwt = JwtConfig(
            secret = jwtSecret,
            expirationHours = jwtExpH
          ),
          aws = AwsConfig(
            accessKeyId = awsKey,
            secretAccessKey = awsSecret,
            region = awsRegion,
            s3Bucket = s3Bucket
          ),
          server = ServerConfig(
            host = serverHost,
            port = serverPort,
            allowedOrigins = origins.split(",").map(_.trim).toList
          ),
          admin = AdminConfig(
            email = adminEmail,
            password = adminPass
          )
        )
    }.load[IO]
