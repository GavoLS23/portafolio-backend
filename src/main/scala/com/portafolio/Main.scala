package com.portafolio

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.portafolio.config.AppConfig
import com.portafolio.http.HttpServer
import com.portafolio.infrastructure.aws.S3Service
import com.portafolio.infrastructure.db.Database
import com.portafolio.repository.*
import com.portafolio.service.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** Punto de entrada de la aplicación.
  *
  * Secuencia de arranque (todo dentro del Resource):
  *   1. Cargar configuración desde variables de entorno (Ciris) 2. Crear pool de conexiones HikariCP 3. Ejecutar migraciones Flyway (crea schema si no existe) 4. Inicializar cliente AWS S3 5. Crear
  *      seed del admin si la BD está vacía 6. Levantar el servidor HTTP (Ember)
  */
object Main extends IOApp:

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def run(args: List[String]): IO[ExitCode] =
    for
      _ <- logger.info("Iniciando Portafolio Backend...")
      config <- AppConfig.load
      _ <- logger.info(s"Configuración cargada. Puerto: ${config.server.port}")
      code <- startServer(config)
    yield code

  private def startServer(config: AppConfig): IO[ExitCode] =
    appResource(config)
      .use { server =>
        for
          _ <- logger.info(s"Servidor listo en http://${server.address}")
          _ <- logger.info("Swagger UI disponible en /docs")
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
      s3 <- S3Service.make(config.aws)

      // ── Migraciones (antes de crear los servicios) ───────────────────────
      _ <- Resource.eval(Database.migrate(config.db))

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
      mediaSvc = MediaService.make(mediaRepo, s3)
      techSvc = TechnologyService.make(techRepo)

      // ── Seed admin inicial (authService ya está en scope) ─────────────────
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
        techSvc
      )
    yield server
