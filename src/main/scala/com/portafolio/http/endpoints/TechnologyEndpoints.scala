package com.portafolio.http.endpoints

import cats.effect.IO
import com.portafolio.domain.common.Ids.TechnologyId
import com.portafolio.domain.common.errors.ErrorResponse
import com.portafolio.domain.technology.{CreateTechnologyRequest, TechnologyResponse}
import com.portafolio.http.middleware.AuthMiddleware
import com.portafolio.service.{AuthService, TechnologyService}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ServerEndpoint

object TechnologyEndpoints:

  private val publicBase =
    endpoint
      .tag("Technologies")
      .in("api" / "v1" / "technologies")
      .errorOut(statusCode.and(jsonBody[ErrorResponse]))

  private val adminBase =
    endpoint
      .tag("Admin - Technologies")
      .securityIn(auth.bearer[String]())
      .errorOut(statusCode.and(jsonBody[ErrorResponse]))
      .in("api" / "v1" / "admin" / "technologies")

  /** GET /api/v1/technologies — Catálogo público de tecnologías. */
  private val listPublic =
    publicBase
      .summary("Listar todas las tecnologías")
      .get
      .out(jsonBody[List[TechnologyResponse]])

  /** POST /api/v1/admin/technologies — Crea una tecnología. */
  private val create =
    adminBase
      .summary("Crear tecnología")
      .post
      .in(jsonBody[CreateTechnologyRequest])
      .out(statusCode(StatusCode.Created).and(jsonBody[TechnologyResponse]))

  /** PUT /api/v1/admin/technologies/:id — Actualiza una tecnología. */
  private val update =
    adminBase
      .summary("Actualizar tecnología")
      .put
      .in(path[TechnologyId]("id"))
      .in(jsonBody[CreateTechnologyRequest])
      .out(jsonBody[TechnologyResponse])

  /** DELETE /api/v1/admin/technologies/:id — Elimina una tecnología. */
  private val delete =
    adminBase
      .summary("Eliminar tecnología")
      .delete
      .in(path[TechnologyId]("id"))
      .out(statusCode(StatusCode.NoContent))

  def serverEndpoints(
      techService: TechnologyService,
      authService: AuthService
  ): List[ServerEndpoint[Any, IO]] =
    val sec = AuthMiddleware.securityLogic(authService)

    List(
      listPublic.serverLogic { _ =>
        techService.listAll.map(Right(_))
      },

      create.serverSecurityLogic(sec).serverLogic { _ => req =>
        techService.create(req).map(_.left.map(AuthMiddleware.toTapirError))
      },

      update.serverSecurityLogic(sec).serverLogic { _ => input =>
        val (id, req) = input
        techService.update(id, req).map(_.left.map(AuthMiddleware.toTapirError))
      },

      delete.serverSecurityLogic(sec).serverLogic { _ => id =>
        techService.delete(id).map(_.left.map(AuthMiddleware.toTapirError))
      }
    )
