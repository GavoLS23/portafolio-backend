package com.portafolio

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.portafolio.config.{AppConfig, StorageConfig}
import com.portafolio.http.HttpServer
import com.portafolio.http.routes.LocalUploadRoutes
import com.portafolio.infrastructure.aws.S3Service
import com.portafolio.infrastructure.db.Database
import com.portafolio.infrastructure.storage.{LocalStorageService, StorageService}
import com.portafolio.repository.*
import com.portafolio.service.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** Punto de entrada de la aplicación.
  *
  * Secuencia de arranque:
  *   1. Carga configuración desde variables de entorno (Ciris). 2. Crea el pool de conexiones HikariCP. 3. Ejecuta migraciones Flyway (crea el schema si no existe). 4. Inicializa el servicio de
  *      almacenamiento según `APP_ENV`:
  *      - `development` → [[LocalStorageService]] (disco local).
  *      - `production` → [[S3Service]] (AWS S3). 5. Crea el seed del admin si la BD está vacía. 6. Levanta el servidor HTTP Ember.
  */
object Main extends IOApp:

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def run(args: List[String]): IO[ExitCode] =
    for
      _ <- logger.info("Iniciando Portafolio Backend...")
      config <- AppConfig.load
      _ <- logger.info(s"Configuración cargada. Ambiente: ${envLabel(config.storage)}. Puerto: ${config.server.port}")
      code <- startServer(config)
    yield code

  private def envLabel(s: StorageConfig): String = s match
    case _: StorageConfig.Local => "development (almacenamiento local)"
    case _: StorageConfig.S3    => "production (AWS S3)"

  private def startServer(config: AppConfig): IO[ExitCode] =
    appResource(config)
      .use { server =>
        for
          _ <- logger.info(s"Servidor listo en http://${server.address}")
          _ <- logger.info("Swagger UI disponible en http://${server.address}/")
          _ <- IO.never
        yield ExitCode.Success
      }
      .handleErrorWith { err =>
        logger.error(err)("Error fatal en el servidor").as(ExitCode.Error)
      }

  private def appResource(config: AppConfig) =
    for
      // ── Infraestructura ──────────────────────────────────────────────────
      xa <- Database.makeTransactor(config.db)

      // ── Migraciones (antes de crear los servicios) ───────────────────────
      _ <- Resource.eval(Database.migrate(config.db))

      // ── Servicio de almacenamiento (dev vs prod) ──────────────────────────
      storage <- storageResource(config.storage)

      // ── Rutas extra para desarrollo (upload/serve de archivos locales) ────
      devRoutes = config.storage match
        case StorageConfig.Local(uploadsDir, _) => Some(LocalUploadRoutes.make(uploadsDir))
        case _: StorageConfig.S3                => None

      // ── Repositories ─────────────────────────────────────────────────────
      userRepo = UserRepository.make(xa)
      projRepo = ProjectRepository.make(xa)
      blogRepo = BlogRepository.make(xa)
      mediaRepo = MediaRepository.make(xa)
      techRepo = TechnologyRepository.make(xa)

      // ── Services ─────────────────────────────────────────────────────────
      authService = AuthService.make(userRepo, config.jwt)
      projectSvc = ProjectService.make(projRepo)
      blogSvc = BlogService.make(blogRepo)
      mediaSvc = MediaService.make(mediaRepo, storage)
      techSvc = TechnologyService.make(techRepo)

      // ── Seed del admin inicial ────────────────────────────────────────────
      _ <- Resource.eval(
        authService.seedAdmin(config.admin.email, config.admin.password.value)
      )

      // ── Servidor HTTP ─────────────────────────────────────────────────────
      server <- HttpServer.make(
        config.server,
        authService,
        projectSvc,
        blogSvc,
        mediaSvc,
        techSvc,
        devRoutes
      )
    yield server

  /** Crea el `StorageService` adecuado según el ambiente configurado. */
  private def storageResource(storageConfig: StorageConfig): Resource[IO, StorageService] =
    storageConfig match
      case StorageConfig.Local(uploadsDir, baseUrl) =>
        Resource.eval(LocalStorageService.make(uploadsDir, baseUrl))
      case StorageConfig.S3(aws) =>
        S3Service.make(aws)
