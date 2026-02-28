package com.portafolio.http.endpoints

import cats.effect.IO
import com.portafolio.domain.common.Ids.MediaId
import com.portafolio.domain.common.errors.ErrorResponse
import com.portafolio.domain.common.Pagination
import com.portafolio.domain.media.*
import com.portafolio.http.middleware.AuthMiddleware
import com.portafolio.service.{AuthService, MediaService}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ServerEndpoint

/** Endpoints de media: presigned upload (flujo S3 directo) y gestión de archivos.
  *
  * Flujo de subida directa a S3:
  *   1. `POST /admin/media/presign`  → URL pre-firmada + mediaId
  *   2. Frontend hace `PUT` directo a S3 (sin pasar por el servidor)
  *   3. `POST /admin/media/confirm`  → guarda metadatos en BD
  */
object MediaEndpoints:

  private val adminBase =
    endpoint
      .tag("Admin - Media")
      .securityIn(auth.bearer[String]())
      .errorOut(statusCode.and(jsonBody[ErrorResponse]))
      .in("api" / "v1" / "admin" / "media")

  /** POST /api/v1/admin/media/presign — Solicita URL pre-firmada para subida directa. */
  private val requestUpload =
    adminBase
      .summary("Solicitar URL pre-firmada para subida directa a S3")
      .post
      .in("presign")
      .in(jsonBody[PresignedUploadRequest])
      .out(statusCode(StatusCode.Created).and(jsonBody[PresignedUploadResponse]))

  /** POST /api/v1/admin/media/confirm — Confirma subida y guarda metadatos. */
  private val confirmUpload =
    adminBase
      .summary("Confirmar subida completada y guardar metadatos")
      .post
      .in("confirm")
      .in(jsonBody[ConfirmUploadRequest])
      .out(jsonBody[MediaResponse])

  /** GET /api/v1/admin/media — Lista todos los archivos de media. */
  private val listMedia =
    adminBase
      .summary("Listar todos los archivos de media")
      .get
      .in(query[Option[Int]]("page").default(Some(1)))
      .in(query[Option[Int]]("pageSize").default(Some(50)))
      .out(jsonBody[List[MediaResponse]])

  /** GET /api/v1/admin/media/:id — Obtiene un archivo por ID. */
  private val getById =
    adminBase
      .summary("Obtener archivo de media por ID")
      .get
      .in(path[MediaId]("id"))
      .out(jsonBody[MediaResponse])

  /** DELETE /api/v1/admin/media/:id — Elimina un archivo (BD + S3). */
  private val deleteMedia =
    adminBase
      .summary("Eliminar archivo de media (BD + S3)")
      .delete
      .in(path[MediaId]("id"))
      .out(statusCode(StatusCode.NoContent))

  def serverEndpoints(
      mediaService: MediaService,
      authService:  AuthService
  ): List[ServerEndpoint[Any, IO]] =
    val sec = AuthMiddleware.securityLogic(authService)

    List(
      requestUpload.serverSecurityLogic(sec).serverLogic { _ => req =>
        mediaService.requestPresignedUpload(req).map(_.left.map(AuthMiddleware.toTapirError))
      },

      confirmUpload.serverSecurityLogic(sec).serverLogic { _ => req =>
        mediaService.confirmUpload(req).map(_.left.map(AuthMiddleware.toTapirError))
      },

      listMedia.serverSecurityLogic(sec).serverLogic { _ => input =>
        val (page, pageSize) = input
        val pg = Pagination(page.getOrElse(1), pageSize.getOrElse(50))
        mediaService.listAll(pg).map { case (items, _) => Right(items) }
      },

      getById.serverSecurityLogic(sec).serverLogic { _ => id =>
        mediaService.findById(id).map(_.left.map(AuthMiddleware.toTapirError))
      },

      deleteMedia.serverSecurityLogic(sec).serverLogic { _ => id =>
        mediaService.delete(id).map(_.left.map(AuthMiddleware.toTapirError))
      }
    )
