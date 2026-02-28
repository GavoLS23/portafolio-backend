package com.portafolio.domain.blog

import com.portafolio.domain.common.Ids.{BlogPostId, MediaId}
import com.portafolio.domain.common.Language
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

import java.time.Instant

/** Estado de publicación del post. */
enum PostStatus(val value: String):
  case Draft extends PostStatus("draft")
  case Published extends PostStatus("published")

object PostStatus:
  def fromString(s: String): Either[String, PostStatus] =
    s match
      case "draft"     => Right(Draft)
      case "published" => Right(Published)
      case other       => Left(s"Estado inválido: $other")

  given Encoder[PostStatus] = Encoder[String].contramap(_.value)
  given Decoder[PostStatus] = Decoder[String].emap(fromString)
  given Schema[PostStatus] = Schema.derivedEnumeration[PostStatus].defaultStringBased

/** Traducción de un blog post en un idioma concreto. */
final case class BlogPostTranslation(
    blogPostId: BlogPostId,
    language: Language,
    title: String,
    excerpt: String,
    content: String
)

/** Entidad principal del blog post. */
final case class BlogPost(
    id: BlogPostId,
    slug: String,
    status: PostStatus,
    thumbnailMediaId: Option[MediaId],
    publishedAt: Option[Instant],
    createdAt: Instant,
    updatedAt: Instant
)

// ── DTOs de request ──────────────────────────────────────────────────────────

final case class BlogTranslationInput(
    language: Language,
    title: String,
    excerpt: String,
    content: String
)
object BlogTranslationInput:
  given Encoder[BlogTranslationInput] = deriveEncoder
  given Decoder[BlogTranslationInput] = deriveDecoder
  given Schema[BlogTranslationInput] = Schema.derived

final case class CreateBlogPostRequest(
    slug: String,
    translations: List[BlogTranslationInput],
    tags: List[String]
)
object CreateBlogPostRequest:
  given Encoder[CreateBlogPostRequest] = deriveEncoder
  given Decoder[CreateBlogPostRequest] = deriveDecoder
  given Schema[CreateBlogPostRequest] = Schema.derived

final case class UpdateBlogPostRequest(
    slug: Option[String],
    status: Option[PostStatus],
    thumbnailMediaId: Option[MediaId],
    translations: Option[List[BlogTranslationInput]],
    tags: Option[List[String]]
)
object UpdateBlogPostRequest:
  given Encoder[UpdateBlogPostRequest] = deriveEncoder
  given Decoder[UpdateBlogPostRequest] = deriveDecoder
  given Schema[UpdateBlogPostRequest] = Schema.derived

// ── DTO de response ──────────────────────────────────────────────────────────

final case class BlogPostResponse(
    id: BlogPostId,
    slug: String,
    status: PostStatus,
    thumbnailMediaId: Option[MediaId],
    publishedAt: Option[Instant],
    translations: List[BlogTranslationInput],
    tags: List[String],
    createdAt: Instant,
    updatedAt: Instant
)
object BlogPostResponse:
  given Encoder[BlogPostResponse] = deriveEncoder
  given Decoder[BlogPostResponse] = deriveDecoder
  given Schema[BlogPostResponse] = Schema.derived
