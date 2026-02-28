package com.portafolio.service

import cats.effect.IO
import cats.syntax.all.*
import com.portafolio.domain.common.errors.AppError
import com.portafolio.domain.common.Ids.ProjectId
import com.portafolio.domain.project.*
import com.portafolio.repository.ProjectRepository

/** Servicio de proyectos: lógica de negocio sobre el CRUD de proyectos. */
trait ProjectService:
  def listAll(onlyPublished: Boolean): IO[List[ProjectResponse]]
  def getById(id: ProjectId): IO[Either[AppError, ProjectResponse]]
  def getBySlug(slug: String): IO[Either[AppError, ProjectResponse]]
  def create(req: CreateProjectRequest): IO[Either[AppError, ProjectResponse]]
  def update(id: ProjectId, req: UpdateProjectRequest): IO[Either[AppError, ProjectResponse]]
  def reorder(req: ReorderRequest): IO[Either[AppError, Unit]]
  def delete(id: ProjectId): IO[Either[AppError, Unit]]

object ProjectService:

  def make(repo: ProjectRepository): ProjectService = new ProjectService:

    def listAll(onlyPublished: Boolean): IO[List[ProjectResponse]] =
      repo.findAll(onlyPublished).flatMap(_.traverse(toResponse))

    def getById(id: ProjectId): IO[Either[AppError, ProjectResponse]] =
      repo.findById(id).flatMap {
        case None    => IO.pure(Left(AppError.NotFound(s"Proyecto no encontrado: ${id.value}")))
        case Some(p) => toResponse(p).map(Right(_))
      }

    def getBySlug(slug: String): IO[Either[AppError, ProjectResponse]] =
      repo.findBySlug(slug).flatMap {
        case None    => IO.pure(Left(AppError.NotFound(s"Proyecto no encontrado: $slug")))
        case Some(p) => toResponse(p).map(Right(_))
      }

    def create(req: CreateProjectRequest): IO[Either[AppError, ProjectResponse]] =
      repo.slugExists(req.slug).flatMap {
        case true  => IO.pure(Left(AppError.Conflict(s"El slug '${req.slug}' ya existe")))
        case false => repo.create(req).flatMap(toResponse).map(Right(_))
      }

    def update(id: ProjectId, req: UpdateProjectRequest): IO[Either[AppError, ProjectResponse]] =
      for
        slugConflict <- req.slug.traverse(slug => repo.slugExists(slug, excludeId = Some(id)))
        result <- slugConflict.filter(identity) match
          case Some(true) =>
            IO.pure(Left(AppError.Conflict("El slug ya está en uso")))
          case _ =>
            repo.update(id, req).flatMap {
              case None    => IO.pure(Left(AppError.NotFound(s"Proyecto no encontrado: ${id.value}")))
              case Some(p) => toResponse(p).map(Right(_))
            }
      yield result

    def reorder(req: ReorderRequest): IO[Either[AppError, Unit]] =
      repo.reorder(req.orderedIds).map(Right(_))

    def delete(id: ProjectId): IO[Either[AppError, Unit]] =
      repo.delete(id).map {
        case true  => Right(())
        case false => Left(AppError.NotFound(s"Proyecto no encontrado: ${id.value}"))
      }

    private def toResponse(p: Project): IO[ProjectResponse] =
      (repo.findTranslations(p.id), repo.findTechnologyIds(p.id)).mapN { (translations, techIds) =>
        ProjectResponse(
          id = p.id,
          slug = p.slug,
          status = p.status,
          displayOrder = p.displayOrder,
          demoUrl = p.demoUrl,
          repositoryUrl = p.repositoryUrl,
          thumbnailMediaId = p.thumbnailMediaId,
          translations = translations.map(t => TranslationInput(t.language, t.title, t.description, t.longDescription)),
          technologyIds = techIds,
          createdAt = p.createdAt,
          updatedAt = p.updatedAt
        )
      }
