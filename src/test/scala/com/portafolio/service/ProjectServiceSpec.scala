package com.portafolio.service

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.portafolio.domain.common.Ids.{MediaId, ProjectId, TechnologyId}
import com.portafolio.domain.common.Language
import com.portafolio.domain.common.errors.AppError
import com.portafolio.domain.project.*
import com.portafolio.repository.ProjectRepository
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.Instant
import java.util.UUID
import scala.collection.mutable

class ProjectServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers:

  /** Repositorio stub en memoria. */
  private class InMemoryProjectRepo extends ProjectRepository:
    private val db = mutable.Map.empty[ProjectId, Project]

    private def mkProject(id: ProjectId, slug: String): Project =
      Project(id, slug, ProjectStatus.Draft, 0, None, None, None, Instant.now, Instant.now)

    def findAll(onlyPublished: Boolean): IO[List[Project]] =
      IO.pure(db.values.filter(p => !onlyPublished || p.status == ProjectStatus.Published).toList)

    def findById(id: ProjectId): IO[Option[Project]]      = IO.pure(db.get(id))
    def findBySlug(slug: String): IO[Option[Project]]     = IO.pure(db.values.find(_.slug == slug))
    def findTranslations(id: ProjectId): IO[List[ProjectTranslation]] = IO.pure(List.empty)
    def findTechnologyIds(id: ProjectId): IO[List[TechnologyId]]      = IO.pure(List.empty)

    def create(req: CreateProjectRequest): IO[Project] =
      val id = ProjectId.generate()
      val p  = mkProject(id, req.slug)
      db += id -> p
      IO.pure(p)

    def update(id: ProjectId, req: UpdateProjectRequest): IO[Option[Project]] =
      IO.pure(db.get(id).map { p =>
        val updated = p.copy(slug = req.slug.getOrElse(p.slug))
        db += id -> updated
        updated
      })

    def updateTranslations(id: ProjectId, ts: List[TranslationInput]): IO[Unit] = IO.unit
    def updateTechnologies(id: ProjectId, ids: List[TechnologyId]): IO[Unit]    = IO.unit
    def reorder(ids: List[ProjectId]): IO[Unit]                                 = IO.unit

    def delete(id: ProjectId): IO[Boolean] =
      IO.pure(db.remove(id).isDefined)

    def slugExists(slug: String, excludeId: Option[ProjectId]): IO[Boolean] =
      IO.pure(db.values.exists(p => p.slug == slug && !excludeId.contains(p.id)))

  "ProjectService" should {

    "crear un proyecto con slug único" in {
      val repo    = new InMemoryProjectRepo
      val service = ProjectService.make(repo)
      val req     = CreateProjectRequest("my-project", None, None, List.empty, List.empty)

      service.create(req).asserting {
        case Right(resp) => resp.slug shouldBe "my-project"
        case Left(err)   => fail(s"Error inesperado: $err")
      }
    }

    "rechazar slug duplicado" in {
      val repo    = new InMemoryProjectRepo
      val service = ProjectService.make(repo)
      val req     = CreateProjectRequest("duplicado", None, None, List.empty, List.empty)

      for
        _      <- service.create(req)
        result <- service.create(req)
      yield result shouldBe Left(AppError.Conflict("El slug 'duplicado' ya existe"))
    }

    "retornar NotFound para ID inexistente" in {
      val repo    = new InMemoryProjectRepo
      val service = ProjectService.make(repo)
      val fakeId  = ProjectId.generate()

      service.getById(fakeId).asserting {
        case Left(AppError.NotFound(_)) => succeed
        case other                      => fail(s"Resultado inesperado: $other")
      }
    }

    "eliminar proyecto existente" in {
      val repo    = new InMemoryProjectRepo
      val service = ProjectService.make(repo)
      val req     = CreateProjectRequest("to-delete", None, None, List.empty, List.empty)

      for
        created <- service.create(req)
        id       = created.getOrElse(fail()).id
        result  <- service.delete(id)
      yield result shouldBe Right(())
    }
  }
