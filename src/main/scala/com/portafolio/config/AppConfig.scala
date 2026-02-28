package com.portafolio.config

import cats.effect.IO
import cats.syntax.all.*
import ciris.*

// ── Configuración de base de datos (HikariCP + Doobie) ──────────────────────

final case class DbConfig(
    url: String,
    user: String,
    password: Secret[String],
    schema: String,
    poolSize: Int
)

// ── Configuración JWT ────────────────────────────────────────────────────────

final case class JwtConfig(
    secret: Secret[String],
    expirationHours: Long
)

// ── Configuración AWS S3 (solo en producción) ────────────────────────────────

final case class AwsConfig(
    accessKeyId: Secret[String],
    secretAccessKey: Secret[String],
    region: String,
    s3Bucket: String
)

// ── Configuración de almacenamiento (depende del ambiente) ───────────────────

/** Determina dónde se guardan los archivos subidos.
  *
  *   - [[StorageConfig.Local]]: disco local, ambiente `development`.
  *   - [[StorageConfig.S3]]: AWS S3, ambiente `production`.
  */
sealed trait StorageConfig

object StorageConfig:
  /** Almacenamiento local en disco.
    * @param uploadsDir
    *   Directorio raíz (default `./uploads`).
    * @param baseUrl
    *   URL base del servidor para construir URLs públicas (default `http://localhost:8080`).
    */
  final case class Local(uploadsDir: String, baseUrl: String) extends StorageConfig

  /** Almacenamiento en AWS S3. */
  final case class S3(aws: AwsConfig) extends StorageConfig

// ── Configuración del servidor HTTP ─────────────────────────────────────────

final case class ServerConfig(
    host: String,
    port: Int,
    allowedOrigins: List[String]
)

// ── Configuración del administrador inicial ──────────────────────────────────

final case class AdminConfig(
    email: String,
    password: Secret[String]
)

// ── Configuración global ─────────────────────────────────────────────────────

/** Configuración global de la aplicación leída desde variables de entorno con Ciris.
  *
  * ==Variables siempre requeridas==
  *   - `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`
  *
  * ==Variables requeridas solo en producción (`APP_ENV=production`)==
  *   - `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `AWS_S3_BUCKET`
  *
  * ==Variables opcionales (con valores por defecto)==
  * {{{
  *   APP_ENV            = development
  *   DB_SCHEMA          = portafolio
  *   DB_POOL_SIZE       = 10
  *   JWT_EXPIRATION_HOURS = 24
  *   SERVER_HOST        = 0.0.0.0
  *   SERVER_PORT        = 8080
  *   ALLOWED_ORIGINS    = http://localhost:4200
  *   LOCAL_UPLOADS_DIR  = ./uploads
  *   BASE_URL           = http://localhost:8080
  * }}}
  */
final case class AppConfig(
    db: DbConfig,
    jwt: JwtConfig,
    storage: StorageConfig,
    server: ServerConfig,
    admin: AdminConfig
)

object AppConfig:

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

  private val serverConfig: ConfigValue[Effect, ServerConfig] =
    (
      env("SERVER_HOST").default("0.0.0.0"),
      env("SERVER_PORT").as[Int].default(8080),
      env("ALLOWED_ORIGINS").default("http://localhost:4200")
    ).mapN { (host, port, origins) =>
      ServerConfig(host, port, origins.split(",").map(_.trim).toList)
    }

  private val adminConfig: ConfigValue[Effect, AdminConfig] =
    (
      env("ADMIN_EMAIL"),
      env("ADMIN_PASSWORD").secret
    ).mapN(AdminConfig.apply)

  /** Resuelve qué implementación de almacenamiento usar según `APP_ENV`.
    *
    * En `development` (default): almacenamiento local, sin credenciales AWS. En `production`: requiere todas las variables `AWS_*`, falla al arrancar si faltan.
    */
  private val storageConfig: ConfigValue[Effect, StorageConfig] =
    (
      env("APP_ENV").default("development"),
      env("AWS_ACCESS_KEY_ID").option,
      env("AWS_SECRET_ACCESS_KEY").option,
      env("AWS_REGION").option,
      env("AWS_S3_BUCKET").option,
      env("LOCAL_UPLOADS_DIR").default("./uploads"),
      env("BASE_URL").default("http://localhost:8080")
    ).mapN { (appEnv, maybeKeyId, maybeSecretKey, maybeRegion, maybeBucket, uploadsDir, baseUrl) =>
      appEnv match
        case "production" =>
          (for
            keyId <- maybeKeyId
            secretKey <- maybeSecretKey
            region <- maybeRegion
            bucket <- maybeBucket
          yield StorageConfig.S3(
            AwsConfig(
              accessKeyId = Secret(keyId),
              secretAccessKey = Secret(secretKey),
              region = region,
              s3Bucket = bucket
            )
          )).getOrElse(
            throw RuntimeException(
              "APP_ENV=production requiere: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION, AWS_S3_BUCKET"
            )
          )
        case _ =>
          StorageConfig.Local(uploadsDir, baseUrl)
    }

  /** Carga la configuración completa. Falla en startup si falta alguna variable obligatoria. */
  def load: IO[AppConfig] =
    (dbConfig, jwtConfig, storageConfig, serverConfig, adminConfig)
      .parMapN(AppConfig.apply)
      .load[IO]
