package com.portafolio.domain.project

import com.portafolio.domain.common.Ids.{MediaId, ProjectId, TechnologyId}
import com.portafolio.domain.common.Language
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

import java.time.Instant

/** Estado de publicación del proyecto. */
enum ProjectStatus(val value: String):
  case Draft extends ProjectStatus("draft")
  case Published extends ProjectStatus("published")

object ProjectStatus:
  def fromString(s: String): Either[String, ProjectStatus] =
    s match
      case "draft"     => Right(Draft)
      case "published" => Right(Published)
      case other       => Left(s"Estado inválido: $other")

  given Encoder[ProjectStatus] = Encoder[String].contramap(_.value)
  given Decoder[ProjectStatus] = Decoder[String].emap(fromString)
  given Schema[ProjectStatus] = Schema.derivedEnumeration[ProjectStatus].defaultStringBased

/** Traducción de un proyecto en un idioma concreto. */
final case class ProjectTranslation(
    projectId: ProjectId,
    language: Language,
    title: String,
    description: String,
    longDescription: String
)

/** Entidad principal de proyecto (sin traducciones cargadas). */
final case class Project(
    id: ProjectId,
    slug: String,
    status: ProjectStatus,
    displayOrder: Int,
    demoUrl: Option[String],
    repositoryUrl: Option[String],
    thumbnailMediaId: Option[MediaId],
    createdAt: Instant,
    updatedAt: Instant
)

/** Proyecto completo con sus traducciones y tecnologías (para respuestas API). */
final case class ProjectDetail(
    project: Project,
    translations: Map[Language, ProjectTranslation],
    technologies: List[TechnologyId]
)

// ── DTOs de request ──────────────────────────────────────────────────────────

final case class TranslationInput(
    language: Language,
    title: String,
    description: String,
    longDescription: String
)
object TranslationInput:
  given Encoder[TranslationInput] = deriveEncoder
  given Decoder[TranslationInput] = deriveDecoder
  given Schema[TranslationInput] = Schema.derived

final case class CreateProjectRequest(
    slug: String,
    demoUrl: Option[String],
    repositoryUrl: Option[String],
    translations: List[TranslationInput],
    technologyIds: List[TechnologyId]
)
object CreateProjectRequest:
  given Encoder[CreateProjectRequest] = deriveEncoder
  given Decoder[CreateProjectRequest] = deriveDecoder
  given Schema[CreateProjectRequest] = Schema.derived

final case class UpdateProjectRequest(
    slug: Option[String],
    status: Option[ProjectStatus],
    demoUrl: Option[String],
    repositoryUrl: Option[String],
    thumbnailMediaId: Option[MediaId],
    translations: Option[List[TranslationInput]],
    technologyIds: Option[List[TechnologyId]]
)
object UpdateProjectRequest:
  given Encoder[UpdateProjectRequest] = deriveEncoder
  given Decoder[UpdateProjectRequest] = deriveDecoder
  given Schema[UpdateProjectRequest] = Schema.derived

final case class ReorderRequest(orderedIds: List[ProjectId])
object ReorderRequest:
  given Encoder[ReorderRequest] = deriveEncoder
  given Decoder[ReorderRequest] = deriveDecoder
  given Schema[ReorderRequest] = Schema.derived

// ── DTOs de response ─────────────────────────────────────────────────────────

final case class ProjectResponse(
    id: ProjectId,
    slug: String,
    status: ProjectStatus,
    displayOrder: Int,
    demoUrl: Option[String],
    repositoryUrl: Option[String],
    thumbnailMediaId: Option[MediaId],
    translations: List[TranslationInput],
    technologyIds: List[TechnologyId],
    createdAt: Instant,
    updatedAt: Instant
)
object ProjectResponse:
  given Encoder[ProjectResponse] = deriveEncoder
  given Decoder[ProjectResponse] = deriveDecoder
  given Schema[ProjectResponse] = Schema.derived
