package com.portafolio.http.endpoints

import cats.effect.IO
import com.portafolio.domain.common.Ids.ProjectId
import com.portafolio.domain.common.errors.ErrorResponse
import com.portafolio.domain.project.*
import com.portafolio.http.middleware.AuthMiddleware
import com.portafolio.service.{AuthService, ProjectService}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

object ProjectEndpoints:

  // ── Base público ─────────────────────────────────────────────────────────
  private val publicBase =
    endpoint
      .tag("Projects")
      .in("api" / "v1" / "projects")
      .errorOut(statusCode.and(jsonBody[ErrorResponse]))

  // ── Base admin (con seguridad JWT) ────────────────────────────────────────
  private val adminBase =
    endpoint
      .tag("Admin - Projects")
      .securityIn(auth.bearer[String]())
      .errorOut(statusCode.and(jsonBody[ErrorResponse]))
      .in("api" / "v1" / "admin" / "projects")

  // ── Endpoints públicos ───────────────────────────────────────────────────

  /** GET /api/v1/projects — Lista proyectos publicados. */
  private val listPublic =
    publicBase
      .summary("Listar proyectos publicados")
      .get
      .out(jsonBody[List[ProjectResponse]])

  /** GET /api/v1/projects/:slug — Obtiene un proyecto por slug. */
  private val getBySlug =
    publicBase
      .summary("Obtener proyecto por slug")
      .get
      .in(path[String]("slug"))
      .out(jsonBody[ProjectResponse])

  // ── Endpoints admin ───────────────────────────────────────────────────────

  /** GET /api/v1/admin/projects — Lista todos (incluyendo borradores). */
  private val listAdmin =
    adminBase
      .summary("Listar todos los proyectos (admin)")
      .get
      .out(jsonBody[List[ProjectResponse]])

  /** POST /api/v1/admin/projects — Crea un proyecto nuevo. */
  private val create =
    adminBase
      .summary("Crear proyecto")
      .post
      .in(jsonBody[CreateProjectRequest])
      .out(statusCode(StatusCode.Created).and(jsonBody[ProjectResponse]))

  /** PUT /api/v1/admin/projects/reorder — Reordena proyectos (drag & drop). */
  private val reorder =
    adminBase
      .summary("Reordenar proyectos (drag & drop)")
      .put
      .in("reorder")
      .in(jsonBody[ReorderRequest])
      .out(statusCode(StatusCode.NoContent))

  /** PUT /api/v1/admin/projects/:id — Actualiza un proyecto. */
  private val update =
    adminBase
      .summary("Actualizar proyecto")
      .put
      .in(path[ProjectId]("id"))
      .in(jsonBody[UpdateProjectRequest])
      .out(jsonBody[ProjectResponse])

  /** DELETE /api/v1/admin/projects/:id — Elimina un proyecto. */
  private val delete =
    adminBase
      .summary("Eliminar proyecto")
      .delete
      .in(path[ProjectId]("id"))
      .out(statusCode(StatusCode.NoContent))

  def serverEndpoints(
      projectService: ProjectService,
      authService: AuthService
  ): List[ServerEndpoint[Any, IO]] =
    val sec = AuthMiddleware.securityLogic(authService)

    List(
      // ── Públicos ─────────────────────────────────────────────────────────
      listPublic.serverLogic { _ =>
        projectService.listAll(onlyPublished = true).map(Right(_))
      },
      getBySlug.serverLogic { slug =>
        projectService.getBySlug(slug).map(_.left.map(AuthMiddleware.toTapirError))
      },

      // ── Admin ─────────────────────────────────────────────────────────────
      listAdmin.serverSecurityLogic(sec).serverLogic { _ => _ =>
        projectService.listAll(onlyPublished = false).map(Right(_))
      },
      create.serverSecurityLogic(sec).serverLogic { _ => req =>
        projectService.create(req).map(_.left.map(AuthMiddleware.toTapirError))
      },

      // reorder va antes que update para que "reorder" literal gane al path param
      reorder.serverSecurityLogic(sec).serverLogic { _ => req =>
        projectService.reorder(req).map(_.left.map(AuthMiddleware.toTapirError))
      },
      update.serverSecurityLogic(sec).serverLogic { _ => input =>
        val (id, req) = input
        projectService.update(id, req).map(_.left.map(AuthMiddleware.toTapirError))
      },
      delete.serverSecurityLogic(sec).serverLogic { _ => id =>
        projectService.delete(id).map(_.left.map(AuthMiddleware.toTapirError))
      }
    )
