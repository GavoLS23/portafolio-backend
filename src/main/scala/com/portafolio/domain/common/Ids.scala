package com.portafolio.domain.common

import java.util.UUID
import scala.util.Try

import doobie.Meta
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}

/** Newtypes para IDs usando opaque types de Scala 3.
  *
  * NOTA: Las instancias de Encoder/Decoder/Meta se construyen de forma directa
  * (sin pasar por `Encoder[UUID]` / `Decoder[UUID]` / `Meta[UUID]`) porque dentro
  * del companion de un opaque type el tipo es transparente, lo que haría que
  * `Encoder[UUID]` resuelva a `given_Encoder_XxxId` creando un bucle infinito.
  */
object Ids:

  // ── UserId ────────────────────────────────────────────────────────────────
  opaque type UserId = UUID
  object UserId:
    def apply(uuid: UUID): UserId = uuid
    def generate(): UserId        = UUID.randomUUID()
    def fromString(s: String): Either[String, UserId] =
      Try(UUID.fromString(s)).toEither.left.map(_.getMessage)
    extension (id: UserId) def value: UUID = id

    given Encoder[UserId] =
      Encoder.instance[UserId](id => Json.fromString(id.toString))

    given Decoder[UserId] =
      Decoder.instance[UserId] { (c: HCursor) =>
        c.as[String].flatMap(s =>
          Try(UserId(UUID.fromString(s))).toEither
            .left.map(e => DecodingFailure(e.getMessage, c.history))
        )
      }

    given Meta[UserId] =
      Meta[String].timap(s => UserId(UUID.fromString(s)))(_.toString)

    given Schema[UserId] =
      Schema.schemaForUUID.map(u => Some(UserId(u)))(_.value)

    given Codec[String, UserId, CodecFormat.TextPlain] =
      Codec.string.mapDecode(s =>
        Try(UUID.fromString(s)).fold(
          e => DecodeResult.Error(s, e),
          u => DecodeResult.Value(UserId(u))
        )
      )(_.toString)

  // ── ProjectId ─────────────────────────────────────────────────────────────
  opaque type ProjectId = UUID
  object ProjectId:
    def apply(uuid: UUID): ProjectId = uuid
    def generate(): ProjectId        = UUID.randomUUID()
    def fromString(s: String): Either[String, ProjectId] =
      Try(UUID.fromString(s)).toEither.left.map(_.getMessage)
    extension (id: ProjectId) def value: UUID = id

    given Encoder[ProjectId] =
      Encoder.instance[ProjectId](id => Json.fromString(id.toString))

    given Decoder[ProjectId] =
      Decoder.instance[ProjectId] { (c: HCursor) =>
        c.as[String].flatMap(s =>
          Try(ProjectId(UUID.fromString(s))).toEither
            .left.map(e => DecodingFailure(e.getMessage, c.history))
        )
      }

    given Meta[ProjectId] =
      Meta[String].timap(s => ProjectId(UUID.fromString(s)))(_.toString)

    given Schema[ProjectId] =
      Schema.schemaForUUID.map(u => Some(ProjectId(u)))(_.value)

    given Codec[String, ProjectId, CodecFormat.TextPlain] =
      Codec.string.mapDecode(s =>
        Try(UUID.fromString(s)).fold(
          e => DecodeResult.Error(s, e),
          u => DecodeResult.Value(ProjectId(u))
        )
      )(_.toString)

  // ── BlogPostId ────────────────────────────────────────────────────────────
  opaque type BlogPostId = UUID
  object BlogPostId:
    def apply(uuid: UUID): BlogPostId = uuid
    def generate(): BlogPostId        = UUID.randomUUID()
    def fromString(s: String): Either[String, BlogPostId] =
      Try(UUID.fromString(s)).toEither.left.map(_.getMessage)
    extension (id: BlogPostId) def value: UUID = id

    given Encoder[BlogPostId] =
      Encoder.instance[BlogPostId](id => Json.fromString(id.toString))

    given Decoder[BlogPostId] =
      Decoder.instance[BlogPostId] { (c: HCursor) =>
        c.as[String].flatMap(s =>
          Try(BlogPostId(UUID.fromString(s))).toEither
            .left.map(e => DecodingFailure(e.getMessage, c.history))
        )
      }

    given Meta[BlogPostId] =
      Meta[String].timap(s => BlogPostId(UUID.fromString(s)))(_.toString)

    given Schema[BlogPostId] =
      Schema.schemaForUUID.map(u => Some(BlogPostId(u)))(_.value)

    given Codec[String, BlogPostId, CodecFormat.TextPlain] =
      Codec.string.mapDecode(s =>
        Try(UUID.fromString(s)).fold(
          e => DecodeResult.Error(s, e),
          u => DecodeResult.Value(BlogPostId(u))
        )
      )(_.toString)

  // ── MediaId ───────────────────────────────────────────────────────────────
  opaque type MediaId = UUID
  object MediaId:
    def apply(uuid: UUID): MediaId = uuid
    def generate(): MediaId        = UUID.randomUUID()
    def fromString(s: String): Either[String, MediaId] =
      Try(UUID.fromString(s)).toEither.left.map(_.getMessage)
    extension (id: MediaId) def value: UUID = id

    given Encoder[MediaId] =
      Encoder.instance[MediaId](id => Json.fromString(id.toString))

    given Decoder[MediaId] =
      Decoder.instance[MediaId] { (c: HCursor) =>
        c.as[String].flatMap(s =>
          Try(MediaId(UUID.fromString(s))).toEither
            .left.map(e => DecodingFailure(e.getMessage, c.history))
        )
      }

    given Meta[MediaId] =
      Meta[String].timap(s => MediaId(UUID.fromString(s)))(_.toString)

    given Schema[MediaId] =
      Schema.schemaForUUID.map(u => Some(MediaId(u)))(_.value)

    given Codec[String, MediaId, CodecFormat.TextPlain] =
      Codec.string.mapDecode(s =>
        Try(UUID.fromString(s)).fold(
          e => DecodeResult.Error(s, e),
          u => DecodeResult.Value(MediaId(u))
        )
      )(_.toString)

  // ── TechnologyId ──────────────────────────────────────────────────────────
  opaque type TechnologyId = UUID
  object TechnologyId:
    def apply(uuid: UUID): TechnologyId = uuid
    def generate(): TechnologyId        = UUID.randomUUID()
    def fromString(s: String): Either[String, TechnologyId] =
      Try(UUID.fromString(s)).toEither.left.map(_.getMessage)
    extension (id: TechnologyId) def value: UUID = id

    given Encoder[TechnologyId] =
      Encoder.instance[TechnologyId](id => Json.fromString(id.toString))

    given Decoder[TechnologyId] =
      Decoder.instance[TechnologyId] { (c: HCursor) =>
        c.as[String].flatMap(s =>
          Try(TechnologyId(UUID.fromString(s))).toEither
            .left.map(e => DecodingFailure(e.getMessage, c.history))
        )
      }

    given Meta[TechnologyId] =
      Meta[String].timap(s => TechnologyId(UUID.fromString(s)))(_.toString)

    given Schema[TechnologyId] =
      Schema.schemaForUUID.map(u => Some(TechnologyId(u)))(_.value)

    given Codec[String, TechnologyId, CodecFormat.TextPlain] =
      Codec.string.mapDecode(s =>
        Try(UUID.fromString(s)).fold(
          e => DecodeResult.Error(s, e),
          u => DecodeResult.Value(TechnologyId(u))
        )
      )(_.toString)
