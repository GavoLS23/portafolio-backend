package com.portafolio.http.endpoints

import cats.effect.IO
import com.portafolio.domain.blog.*
import com.portafolio.domain.common.Ids.BlogPostId
import com.portafolio.domain.common.errors.ErrorResponse
import com.portafolio.domain.common.Pagination
import com.portafolio.http.middleware.AuthMiddleware
import com.portafolio.service.{AuthService, BlogService}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ServerEndpoint

object BlogEndpoints:

  private val publicBase =
    endpoint
      .tag("Blog")
      .in("api" / "v1" / "blog")
      .errorOut(statusCode.and(jsonBody[ErrorResponse]))

  private val adminBase =
    endpoint
      .tag("Admin - Blog")
      .securityIn(auth.bearer[String]())
      .errorOut(statusCode.and(jsonBody[ErrorResponse]))
      .in("api" / "v1" / "admin" / "blog")

  // ── Públicos ──────────────────────────────────────────────────────────────

  /** GET /api/v1/blog — Lista posts publicados con paginación. */
  private val listPublic =
    publicBase
      .summary("Listar posts publicados")
      .get
      .in(query[Option[Int]]("page").default(Some(1)))
      .in(query[Option[Int]]("pageSize").default(Some(20)))
      .out(jsonBody[List[BlogPostResponse]])

  /** GET /api/v1/blog/:slug — Obtiene un post por slug. */
  private val getBySlug =
    publicBase
      .summary("Obtener post por slug")
      .get
      .in(path[String]("slug"))
      .out(jsonBody[BlogPostResponse])

  // ── Admin ─────────────────────────────────────────────────────────────────

  /** GET /api/v1/admin/blog — Lista todos los posts (incluyendo borradores). */
  private val listAdmin =
    adminBase
      .summary("Listar todos los posts (admin)")
      .get
      .in(query[Option[Int]]("page").default(Some(1)))
      .in(query[Option[Int]]("pageSize").default(Some(20)))
      .out(jsonBody[List[BlogPostResponse]])

  /** POST /api/v1/admin/blog — Crea un post. */
  private val create =
    adminBase
      .summary("Crear post")
      .post
      .in(jsonBody[CreateBlogPostRequest])
      .out(statusCode(StatusCode.Created).and(jsonBody[BlogPostResponse]))

  /** PUT /api/v1/admin/blog/:id — Actualiza un post. */
  private val update =
    adminBase
      .summary("Actualizar post")
      .put
      .in(path[BlogPostId]("id"))
      .in(jsonBody[UpdateBlogPostRequest])
      .out(jsonBody[BlogPostResponse])

  /** DELETE /api/v1/admin/blog/:id — Elimina un post. */
  private val delete =
    adminBase
      .summary("Eliminar post")
      .delete
      .in(path[BlogPostId]("id"))
      .out(statusCode(StatusCode.NoContent))

  def serverEndpoints(
      blogService: BlogService,
      authService: AuthService
  ): List[ServerEndpoint[Any, IO]] =
    val sec = AuthMiddleware.securityLogic(authService)

    List(
      listPublic.serverLogic { case (page, pageSize) =>
        val pg = Pagination(page.getOrElse(1), pageSize.getOrElse(20))
        blogService.listAll(onlyPublished = true, pg).map { case (posts, _) => Right(posts) }
      },
      getBySlug.serverLogic { slug =>
        blogService.getBySlug(slug).map(_.left.map(AuthMiddleware.toTapirError))
      },
      listAdmin.serverSecurityLogic(sec).serverLogic { _ => input =>
        val (page, pageSize) = input
        val pg = Pagination(page.getOrElse(1), pageSize.getOrElse(20))
        blogService.listAll(onlyPublished = false, pg).map { case (posts, _) => Right(posts) }
      },
      create.serverSecurityLogic(sec).serverLogic { _ => req =>
        blogService.create(req).map(_.left.map(AuthMiddleware.toTapirError))
      },
      update.serverSecurityLogic(sec).serverLogic { _ => input =>
        val (id, req) = input
        blogService.update(id, req).map(_.left.map(AuthMiddleware.toTapirError))
      },
      delete.serverSecurityLogic(sec).serverLogic { _ => id =>
        blogService.delete(id).map(_.left.map(AuthMiddleware.toTapirError))
      }
    )
