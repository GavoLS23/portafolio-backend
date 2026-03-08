package com.portafolio.http

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.portafolio.config.ServerConfig
import com.portafolio.http.endpoints.*
import com.portafolio.http.websocket.PreviewWebSocket
import com.portafolio.service.*
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.http4s.server.middleware.{CORS, Logger as HttpLogger}
import org.typelevel.log4cats.Logger
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.duration.*

/** Ensambla todos los endpoints, middlewares y levanta el servidor Ember.
  *
  * Swagger UI disponible en "/"
  */
object HttpServer:

  def make(
      config: ServerConfig,
      authService: AuthService,
      projectService: ProjectService,
      blogService: BlogService,
      mediaService: MediaService,
      techService: TechnologyService,
      devRoutes: Option[HttpRoutes[IO]] = None
  )(using logger: Logger[IO]): Resource[IO, Server] =

    // ── Endpoints Tapir ───────────────────────────────────────────────────
    val allEndpoints =
      AuthEndpoints.serverEndpoints(authService) ++
        ProjectEndpoints.serverEndpoints(projectService, authService) ++
        BlogEndpoints.serverEndpoints(blogService, authService) ++
        MediaEndpoints.serverEndpoints(mediaService, authService) ++
        TechnologyEndpoints.serverEndpoints(techService, authService)

    // ── Swagger UI en "/" ─────────────────────────────────────────────────
    val swaggerEndpoints =
      SwaggerInterpreter(
        swaggerUIOptions = sttp.tapir.swagger.SwaggerUIOptions.default.copy(pathPrefix = List.empty)
      ).fromServerEndpoints[IO](allEndpoints, "Portfolio API", "1.0.0")

    // ── Convertir endpoints a HttpRoutes ──────────────────────────────────
    val apiRoutes = Http4sServerInterpreter[IO]().toRoutes(allEndpoints)
    val swaggerRoutes = Http4sServerInterpreter[IO]().toRoutes(swaggerEndpoints)

    // ── Política CORS (CLAVE) ─────────────────────────────────────────────
    val corsPolicy =
      CORS.policy.withAllowOriginAll.withAllowMethodsAll.withAllowHeadersAll
        .withMaxAge(1.day)

    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(config.host).getOrElse(host"0.0.0.0"))
      .withPort(Port.fromInt(config.port).getOrElse(port"8080"))
      .withHttpWebSocketApp { wsBuilder =>

        val wsRoutes = PreviewWebSocket.routes(authService, wsBuilder)
        val extra = devRoutes.getOrElse(HttpRoutes.empty[IO])

        // ── TODAS las routes juntas ───────────────────────────────────────
        val routes: HttpRoutes[IO] =
          extra <+> wsRoutes <+> apiRoutes <+> swaggerRoutes

        // ── CORS DEBE IR AQUÍ (HttpRoutes, no HttpApp) ─────────────────────
        val corsRoutes: HttpRoutes[IO] =
          corsPolicy.httpRoutes(routes)

        // ── HttpApp final con logging ─────────────────────────────────────
        val app: HttpApp[IO] =
          HttpLogger.httpApp(logHeaders = true, logBody = false)(
            corsRoutes.orNotFound
          )

        app
      }
      .build
