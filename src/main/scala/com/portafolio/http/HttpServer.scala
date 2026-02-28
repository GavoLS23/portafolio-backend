package com.portafolio.http

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.portafolio.config.{AppConfig, ServerConfig}
import com.portafolio.http.endpoints.*
import com.portafolio.http.websocket.PreviewWebSocket
import com.portafolio.service.*
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.http4s.server.middleware.{CORS, CORSConfig, Logger as HttpLogger}
import org.typelevel.log4cats.Logger
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.duration.*

/** Ensambla todos los endpoints, middlewares y levanta el servidor Ember. */
object HttpServer:

  def make(
      config: ServerConfig,
      authService: AuthService,
      projectService: ProjectService,
      blogService: BlogService,
      mediaService: MediaService,
      techService: TechnologyService
  )(using logger: Logger[IO]): Resource[IO, Server] =

    // ── Recolectar todos los ServerEndpoints ──────────────────────────────
    val allEndpoints =
      AuthEndpoints.serverEndpoints(authService) ++
        ProjectEndpoints.serverEndpoints(projectService, authService) ++
        BlogEndpoints.serverEndpoints(blogService, authService) ++
        MediaEndpoints.serverEndpoints(mediaService, authService) ++
        TechnologyEndpoints.serverEndpoints(techService, authService)

    // ── Swagger UI en /docs ───────────────────────────────────────────────
    val swaggerEndpoints = SwaggerInterpreter()
      .fromServerEndpoints[IO](allEndpoints, "Portfolio API", "1.0.0")

    // ── Convertir a Http4s routes ─────────────────────────────────────────
    val apiRoutes = Http4sServerInterpreter[IO]().toRoutes(allEndpoints)
    val swaggerRoutes = Http4sServerInterpreter[IO]().toRoutes(swaggerEndpoints)

    // ── CORS ──────────────────────────────────────────────────────────────
    val corsPolicy = CORS.policy
      .withAllowOriginHost(config.allowedOrigins.toSet)
      .withAllowCredentials(true)
      .withMaxAge(1.day)

    // ── WebSocket (se añade fuera de tapir) ───────────────────────────────
    // Se instancia dentro del Resource para obtener el WebSocketBuilder2
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(config.host).getOrElse(host"0.0.0.0"))
      .withPort(Port.fromInt(config.port).getOrElse(port"8080"))
      .withHttpWebSocketApp { wsBuilder =>
        val wsRoutes = PreviewWebSocket.routes(authService, wsBuilder)

        val app: HttpApp[IO] =
          corsPolicy.apply(
            HttpLogger.httpApp(logHeaders = true, logBody = false)(
              (wsRoutes <+> apiRoutes <+> swaggerRoutes).orNotFound
            )
          )
        app
      }
      .build
