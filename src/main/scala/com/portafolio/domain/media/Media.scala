package com.portafolio.domain.media

import com.portafolio.domain.common.Ids.MediaId
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

import java.time.Instant

enum MediaType(val value: String):
  case Image extends MediaType("image")
  case Video extends MediaType("video")

object MediaType:
  def fromString(s: String): Either[String, MediaType] =
    s match
      case "image" => Right(Image)
      case "video" => Right(Video)
      case other   => Left(s"Tipo de media inválido: $other")

  given Encoder[MediaType] = Encoder[String].contramap(_.value)
  given Decoder[MediaType] = Decoder[String].emap(fromString)
  given Schema[MediaType] = Schema.derivedEnumeration[MediaType].defaultStringBased

/** Entidad de media almacenada en S3. */
final case class Media(
    id: MediaId,
    s3Key: String,
    s3Bucket: String,
    filename: String,
    mimeType: String,
    mediaType: MediaType,
    sizeBytes: Long,
    widthPx: Option[Int],
    heightPx: Option[Int],
    durationS: Option[Int],
    createdAt: Instant
)

/** Solicitud de URL pre-firmada para subir un archivo directamente a S3. */
final case class PresignedUploadRequest(
    filename: String,
    mimeType: String,
    mediaType: MediaType,
    sizeBytes: Long
)
object PresignedUploadRequest:
  given Encoder[PresignedUploadRequest] = deriveEncoder
  given Decoder[PresignedUploadRequest] = deriveDecoder
  given Schema[PresignedUploadRequest] = Schema.derived

/** URL pre-firmada devuelta al frontend para hacer PUT directo a S3. */
final case class PresignedUploadResponse(
    uploadUrl: String,
    mediaId: MediaId,
    s3Key: String,
    expiresInS: Int,
    publicUrl: String
)
object PresignedUploadResponse:
  given Encoder[PresignedUploadResponse] = deriveEncoder
  given Decoder[PresignedUploadResponse] = deriveDecoder
  given Schema[PresignedUploadResponse] = Schema.derived

/** Confirmación de que la subida a S3 se completó con éxito. */
final case class ConfirmUploadRequest(
    mediaId: MediaId,
    widthPx: Option[Int],
    heightPx: Option[Int],
    durationS: Option[Int]
)
object ConfirmUploadRequest:
  given Encoder[ConfirmUploadRequest] = deriveEncoder
  given Decoder[ConfirmUploadRequest] = deriveDecoder
  given Schema[ConfirmUploadRequest] = Schema.derived

final case class MediaResponse(
    id: MediaId,
    url: String,
    filename: String,
    mimeType: String,
    mediaType: MediaType,
    sizeBytes: Long,
    widthPx: Option[Int],
    heightPx: Option[Int],
    durationS: Option[Int],
    createdAt: Instant
)
object MediaResponse:
  given Encoder[MediaResponse] = deriveEncoder
  given Decoder[MediaResponse] = deriveDecoder
  given Schema[MediaResponse] = Schema.derived
