package com.portafolio.service

import cats.effect.IO
import cats.syntax.all.*
import com.portafolio.domain.blog.*
import com.portafolio.domain.common.Ids.BlogPostId
import com.portafolio.domain.common.errors.AppError
import com.portafolio.domain.common.Pagination
import com.portafolio.repository.BlogRepository

/** Servicio de blog: lógica de negocio sobre posts, traducciones y tags. */
trait BlogService:
  def listAll(onlyPublished: Boolean, pagination: Pagination): IO[(List[BlogPostResponse], Long)]
  def getById(id: BlogPostId): IO[Either[AppError, BlogPostResponse]]
  def getBySlug(slug: String): IO[Either[AppError, BlogPostResponse]]
  def create(req: CreateBlogPostRequest): IO[Either[AppError, BlogPostResponse]]
  def update(id: BlogPostId, req: UpdateBlogPostRequest): IO[Either[AppError, BlogPostResponse]]
  def delete(id: BlogPostId): IO[Either[AppError, Unit]]

object BlogService:

  def make(repo: BlogRepository): BlogService = new BlogService:

    def listAll(onlyPublished: Boolean, pagination: Pagination): IO[(List[BlogPostResponse], Long)] =
      (
        repo.findAll(onlyPublished, pagination.limit, pagination.offset).flatMap(_.traverse(toResponse)),
        repo.countAll(onlyPublished)
      ).tupled

    def getById(id: BlogPostId): IO[Either[AppError, BlogPostResponse]] =
      repo.findById(id).flatMap {
        case None    => IO.pure(Left(AppError.NotFound(s"Post no encontrado: ${id.value}")))
        case Some(p) => toResponse(p).map(Right(_))
      }

    def getBySlug(slug: String): IO[Either[AppError, BlogPostResponse]] =
      repo.findBySlug(slug).flatMap {
        case None    => IO.pure(Left(AppError.NotFound(s"Post no encontrado: $slug")))
        case Some(p) => toResponse(p).map(Right(_))
      }

    def create(req: CreateBlogPostRequest): IO[Either[AppError, BlogPostResponse]] =
      repo.slugExists(req.slug).flatMap {
        case true  => IO.pure(Left(AppError.Conflict(s"El slug '${req.slug}' ya existe")))
        case false => repo.create(req).flatMap(toResponse).map(Right(_))
      }

    def update(id: BlogPostId, req: UpdateBlogPostRequest): IO[Either[AppError, BlogPostResponse]] =
      for
        slugConflict <- req.slug.traverse(slug => repo.slugExists(slug, excludeId = Some(id)))
        result <- slugConflict.filter(identity) match
          case Some(true) =>
            IO.pure(Left(AppError.Conflict("El slug ya está en uso")))
          case _ =>
            repo.update(id, req).flatMap {
              case None    => IO.pure(Left(AppError.NotFound(s"Post no encontrado: ${id.value}")))
              case Some(p) => toResponse(p).map(Right(_))
            }
      yield result

    def delete(id: BlogPostId): IO[Either[AppError, Unit]] =
      repo.delete(id).map {
        case true  => Right(())
        case false => Left(AppError.NotFound(s"Post no encontrado: ${id.value}"))
      }

    private def toResponse(p: BlogPost): IO[BlogPostResponse] =
      (repo.findTranslations(p.id), repo.findTags(p.id)).mapN { (translations, tags) =>
        BlogPostResponse(
          id = p.id,
          slug = p.slug,
          status = p.status,
          thumbnailMediaId = p.thumbnailMediaId,
          publishedAt = p.publishedAt,
          translations = translations.map(t => BlogTranslationInput(t.language, t.title, t.excerpt, t.content)),
          tags = tags,
          createdAt = p.createdAt,
          updatedAt = p.updatedAt
        )
      }
