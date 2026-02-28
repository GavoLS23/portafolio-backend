package com.portafolio.service

import cats.effect.IO
import cats.syntax.all.*
import com.portafolio.domain.common.Ids.MediaId
import com.portafolio.domain.common.errors.AppError
import com.portafolio.domain.common.Pagination
import com.portafolio.domain.media.*
import com.portafolio.infrastructure.aws.S3Service
import com.portafolio.repository.MediaRepository

/** Servicio de media: gestiona el flujo de subida a S3 y la metadata en BD.
  *
  * Flujo de subida directo:
  *   1. Frontend pide URL pre-firmada → `requestPresignedUpload`
  *   2. Frontend hace PUT directo a S3
  *   3. Frontend confirma la subida → `confirmUpload`
  */
trait MediaService:
  def requestPresignedUpload(req: PresignedUploadRequest): IO[Either[AppError, PresignedUploadResponse]]
  def confirmUpload(req: ConfirmUploadRequest): IO[Either[AppError, MediaResponse]]
  def findById(id: MediaId): IO[Either[AppError, MediaResponse]]
  def listAll(pagination: Pagination): IO[(List[MediaResponse], Long)]
  def delete(id: MediaId): IO[Either[AppError, Unit]]

object MediaService:

  def make(mediaRepo: MediaRepository, s3: S3Service): MediaService = new MediaService:

    def requestPresignedUpload(req: PresignedUploadRequest): IO[Either[AppError, PresignedUploadResponse]] =
      s3.generatePresignedPutUrl(req).flatMap { presigned =>
        // Guarda el registro en BD antes de la subida (estado "pendiente")
        mediaRepo.create(
          mediaId   = presigned.mediaId,
          s3Key     = presigned.s3Key,
          s3Bucket  = s3.bucket,
          filename  = req.filename,
          mimeType  = req.mimeType,
          mediaType = req.mediaType,
          sizeBytes = req.sizeBytes
        ).map(_ => Right(presigned))
      }.handleErrorWith { err =>
        IO.pure(Left(AppError.InternalError(s"Error generando URL de subida: ${err.getMessage}")))
      }

    def confirmUpload(req: ConfirmUploadRequest): IO[Either[AppError, MediaResponse]] =
      mediaRepo.confirmUpload(req.mediaId, req.widthPx, req.heightPx, req.durationS).map {
        case None    => Left(AppError.NotFound(s"Media no encontrada: ${req.mediaId.value}"))
        case Some(m) => Right(toResponse(m))
      }

    def findById(id: MediaId): IO[Either[AppError, MediaResponse]] =
      mediaRepo.findById(id).map {
        case None    => Left(AppError.NotFound(s"Media no encontrada: ${id.value}"))
        case Some(m) => Right(toResponse(m))
      }

    def listAll(pagination: Pagination): IO[(List[MediaResponse], Long)] =
      (
        mediaRepo.findAll(pagination.limit, pagination.offset).map(_.map(toResponse)),
        mediaRepo.countAll
      ).tupled

    def delete(id: MediaId): IO[Either[AppError, Unit]] =
      mediaRepo.delete(id).flatMap {
        case None      => IO.pure(Left(AppError.NotFound(s"Media no encontrada: ${id.value}")))
        case Some(key) => s3.deleteObject(key).map(Right(_))
      }

    private def toResponse(m: Media): MediaResponse =
      MediaResponse(
        id        = m.id,
        url       = s3.publicUrl(m.s3Key),
        filename  = m.filename,
        mimeType  = m.mimeType,
        mediaType = m.mediaType,
        sizeBytes = m.sizeBytes,
        widthPx   = m.widthPx,
        heightPx  = m.heightPx,
        durationS = m.durationS,
        createdAt = m.createdAt
      )
