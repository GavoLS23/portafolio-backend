package com.portafolio.service

import cats.effect.IO
import com.portafolio.domain.common.Ids.TechnologyId
import com.portafolio.domain.common.errors.AppError
import com.portafolio.domain.technology.{CreateTechnologyRequest, Technology, TechnologyResponse}
import com.portafolio.repository.TechnologyRepository

/** Servicio de tecnologías: gestiona el catálogo de tecnologías asignables a proyectos. */
trait TechnologyService:
  def listAll: IO[List[TechnologyResponse]]
  def getById(id: TechnologyId): IO[Either[AppError, TechnologyResponse]]
  def create(req: CreateTechnologyRequest): IO[Either[AppError, TechnologyResponse]]
  def update(id: TechnologyId, req: CreateTechnologyRequest): IO[Either[AppError, TechnologyResponse]]
  def delete(id: TechnologyId): IO[Either[AppError, Unit]]

object TechnologyService:

  def make(repo: TechnologyRepository): TechnologyService = new TechnologyService:

    def listAll: IO[List[TechnologyResponse]] =
      repo.findAll.map(_.map(toResponse))

    def getById(id: TechnologyId): IO[Either[AppError, TechnologyResponse]] =
      repo.findById(id).map {
        case None    => Left(AppError.NotFound(s"Tecnología no encontrada: ${id.value}"))
        case Some(t) => Right(toResponse(t))
      }

    def create(req: CreateTechnologyRequest): IO[Either[AppError, TechnologyResponse]] =
      repo
        .create(req.name, req.iconUrl)
        .map(t => Right(toResponse(t)))
        .handleErrorWith { _ =>
          IO.pure(Left(AppError.Conflict(s"La tecnología '${req.name}' ya existe")))
        }

    def update(id: TechnologyId, req: CreateTechnologyRequest): IO[Either[AppError, TechnologyResponse]] =
      repo.update(id, req.name, req.iconUrl).map {
        case None    => Left(AppError.NotFound(s"Tecnología no encontrada: ${id.value}"))
        case Some(t) => Right(toResponse(t))
      }

    def delete(id: TechnologyId): IO[Either[AppError, Unit]] =
      repo.delete(id).map {
        case true  => Right(())
        case false => Left(AppError.NotFound(s"Tecnología no encontrada: ${id.value}"))
      }

    private def toResponse(t: Technology): TechnologyResponse =
      TechnologyResponse(id = t.id, name = t.name, iconUrl = t.iconUrl)
