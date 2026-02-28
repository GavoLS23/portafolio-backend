package com.portafolio.repository

import cats.effect.IO
import cats.syntax.all.*
import com.portafolio.domain.common.Ids.{MediaId, ProjectId, TechnologyId}
import com.portafolio.domain.common.Language
import com.portafolio.domain.project.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.time.Instant
import java.util.UUID

/** Repositorio de proyectos con traducciones, media y tecnologías. */
trait ProjectRepository:
  def findAll(onlyPublished: Boolean): IO[List[Project]]
  def findById(id: ProjectId): IO[Option[Project]]
  def findBySlug(slug: String): IO[Option[Project]]
  def findTranslations(projectId: ProjectId): IO[List[ProjectTranslation]]
  def findTechnologyIds(projectId: ProjectId): IO[List[TechnologyId]]
  def create(req: CreateProjectRequest): IO[Project]
  def update(id: ProjectId, req: UpdateProjectRequest): IO[Option[Project]]
  def updateTranslations(projectId: ProjectId, translations: List[TranslationInput]): IO[Unit]
  def updateTechnologies(projectId: ProjectId, techIds: List[TechnologyId]): IO[Unit]
  def reorder(orderedIds: List[ProjectId]): IO[Unit]
  def delete(id: ProjectId): IO[Boolean]
  def slugExists(slug: String, excludeId: Option[ProjectId] = None): IO[Boolean]

object ProjectRepository:

  def make(xa: Transactor[IO]): ProjectRepository = new ProjectRepository:

    import com.portafolio.domain.common.Ids.ProjectId.given
    import com.portafolio.domain.common.Ids.MediaId.given
    import com.portafolio.domain.common.Ids.TechnologyId.given

    given Meta[ProjectStatus] = Meta[String].timap(
      s => ProjectStatus.fromString(s).getOrElse(ProjectStatus.Draft)
    )(_.value)

    given Meta[Language] = Meta[String].timap(
      s => Language.fromCode(s).getOrElse(Language.Es)
    )(_.code)

    private type ProjectRow = (UUID, String, String, Int, Option[String], Option[String], Option[UUID], Instant, Instant)

    private def toProject(row: ProjectRow): Project =
      val (id, slug, status, order, demo, repo, thumb, ca, ua) = row
      Project(
        id               = ProjectId(id),
        slug             = slug,
        status           = ProjectStatus.fromString(status).getOrElse(ProjectStatus.Draft),
        displayOrder     = order,
        demoUrl          = demo,
        repositoryUrl    = repo,
        thumbnailMediaId = thumb.map(MediaId(_)),
        createdAt        = ca,
        updatedAt        = ua
      )

    private val selectProject =
      fr"""SELECT id, slug, status::text, display_order, demo_url, repository_url,
                  thumbnail_media_id, created_at, updated_at
           FROM projects"""

    def findAll(onlyPublished: Boolean): IO[List[Project]] =
      val cond = if onlyPublished then fr"WHERE status = 'published'" else fr""
      (selectProject ++ cond ++ fr"ORDER BY display_order ASC, created_at DESC")
        .query[ProjectRow]
        .map(toProject)
        .to[List]
        .transact(xa)

    def findById(id: ProjectId): IO[Option[Project]] =
      (selectProject ++ fr"WHERE id = ${id.value}")
        .query[ProjectRow]
        .map(toProject)
        .option
        .transact(xa)

    def findBySlug(slug: String): IO[Option[Project]] =
      (selectProject ++ fr"WHERE slug = $slug")
        .query[ProjectRow]
        .map(toProject)
        .option
        .transact(xa)

    def findTranslations(projectId: ProjectId): IO[List[ProjectTranslation]] =
      sql"""SELECT project_id, language, title, description, long_description
            FROM project_translations WHERE project_id = ${projectId.value}"""
        .query[(UUID, String, String, String, String)]
        .map { case (pid, lang, title, desc, longDesc) =>
          ProjectTranslation(
            projectId       = ProjectId(pid),
            language        = Language.fromCode(lang).getOrElse(Language.Es),
            title           = title,
            description     = desc,
            longDescription = longDesc
          )
        }
        .to[List]
        .transact(xa)

    def findTechnologyIds(projectId: ProjectId): IO[List[TechnologyId]] =
      sql"SELECT technology_id FROM project_technologies WHERE project_id = ${projectId.value}"
        .query[UUID]
        .map(TechnologyId(_))
        .to[List]
        .transact(xa)

    def create(req: CreateProjectRequest): IO[Project] =
      (for
        project <- sql"""
          INSERT INTO projects (slug, demo_url, repository_url)
          VALUES (${req.slug}, ${req.demoUrl}, ${req.repositoryUrl})
          RETURNING id, slug, status::text, display_order, demo_url,
                    repository_url, thumbnail_media_id, created_at, updated_at
        """
          .query[ProjectRow]
          .map(toProject)
          .unique
        _ <- insertTranslations(project.id, req.translations)
        _ <- setTechnologies(project.id, req.technologyIds)
      yield project).transact(xa)

    def update(id: ProjectId, req: UpdateProjectRequest): IO[Option[Project]] =
      (for
        updated <- updateFields(id, req)
        _       <- updated.traverse_ { p =>
                     req.translations.traverse_(ts => insertTranslations(p.id, ts)) *>
                     req.technologyIds.traverse_(ids => setTechnologies(p.id, ids))
                   }
      yield updated).transact(xa)

    def updateTranslations(projectId: ProjectId, translations: List[TranslationInput]): IO[Unit] =
      insertTranslations(projectId, translations).transact(xa)

    def updateTechnologies(projectId: ProjectId, techIds: List[TechnologyId]): IO[Unit] =
      setTechnologies(projectId, techIds).transact(xa)

    def reorder(orderedIds: List[ProjectId]): IO[Unit] =
      orderedIds.zipWithIndex.traverse_ { case (id, idx) =>
        sql"UPDATE projects SET display_order = $idx WHERE id = ${id.value}".update.run
      }.transact(xa)

    def delete(id: ProjectId): IO[Boolean] =
      sql"DELETE FROM projects WHERE id = ${id.value}"
        .update.run.map(_ > 0).transact(xa)

    def slugExists(slug: String, excludeId: Option[ProjectId]): IO[Boolean] =
      excludeId match
        case None =>
          sql"SELECT EXISTS(SELECT 1 FROM projects WHERE slug = $slug)"
            .query[Boolean].unique.transact(xa)
        case Some(eid) =>
          sql"SELECT EXISTS(SELECT 1 FROM projects WHERE slug = $slug AND id <> ${eid.value})"
            .query[Boolean].unique.transact(xa)

    // ── Helpers internos (dentro de una misma transacción) ──────────────────

    private def insertTranslations(
        projectId: ProjectId,
        translations: List[TranslationInput]
    ): ConnectionIO[Unit] =
      sql"DELETE FROM project_translations WHERE project_id = ${projectId.value}".update.run *>
        translations.traverse_ { t =>
          sql"""
            INSERT INTO project_translations
              (project_id, language, title, description, long_description)
            VALUES
              (${projectId.value}, ${t.language.code}, ${t.title},
               ${t.description}, ${t.longDescription})
          """.update.run
        }

    private def setTechnologies(
        projectId: ProjectId,
        techIds: List[TechnologyId]
    ): ConnectionIO[Unit] =
      sql"DELETE FROM project_technologies WHERE project_id = ${projectId.value}".update.run *>
        techIds.traverse_ { tid =>
          sql"""
            INSERT INTO project_technologies (project_id, technology_id)
            VALUES (${projectId.value}, ${tid.value})
          """.update.run
        }

    private def updateFields(id: ProjectId, req: UpdateProjectRequest): ConnectionIO[Option[Project]] =
      // Construye el SET dinámicamente solo con los campos presentes
      val sets = List(
        req.slug.map(v => fr"slug = $v"),
        req.status.map(v => fr"status = ${v.value}::content_status"),
        req.demoUrl.map(v => fr"demo_url = $v"),
        req.repositoryUrl.map(v => fr"repository_url = $v"),
        req.thumbnailMediaId.map(v => fr"thumbnail_media_id = ${v.value}")
      ).flatten

      if sets.isEmpty then
        (selectProject ++ fr"WHERE id = ${id.value}")
          .query[ProjectRow].map(toProject).option
      else
        val setClause = sets.reduce(_ ++ fr", " ++ _)
        (fr"UPDATE projects SET " ++ setClause ++
          fr"WHERE id = ${id.value}" ++
          fr"""RETURNING id, slug, status::text, display_order, demo_url,
                        repository_url, thumbnail_media_id, created_at, updated_at""")
          .query[ProjectRow].map(toProject).option
