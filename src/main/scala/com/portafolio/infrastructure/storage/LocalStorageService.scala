package com.portafolio.infrastructure.storage

import cats.effect.IO
import com.portafolio.domain.common.Ids.MediaId
import com.portafolio.domain.media.{MediaType, PresignedUploadRequest, PresignedUploadResponse}

import java.nio.file.{Files, Paths}

/** Implementación de [[StorageService]] para el ambiente de desarrollo.
  *
  * Los archivos se guardan en el directorio `uploadsDir` (p. ej. `./uploads`). No hay comunicación con AWS: todo ocurre localmente.
  *
  * Rutas que usa este servicio (gestionadas por [[com.portafolio.http.routes.LocalUploadRoutes]]):
  *   - Subida : `PUT {baseUrl}/api/v1/dev/upload/{folder}/{uuid}/{filename}`
  *   - Descarga: `GET {baseUrl}/api/v1/dev/files/{folder}/{uuid}/{filename}`
  *
  * @param uploadsDir
  *   Directorio raíz donde se almacenan los archivos.
  * @param baseUrl
  *   URL base del servidor (p. ej. `http://localhost:8080`).
  */
final class LocalStorageService(uploadsDir: String, baseUrl: String) extends StorageService:

  def bucket: String = uploadsDir

  /** Genera una respuesta con una URL que apunta al propio backend para subir el archivo. */
  def generatePresignedPutUrl(req: PresignedUploadRequest): IO[PresignedUploadResponse] =
    IO {
      val mediaId = MediaId.generate()
      val folder = req.mediaType match
        case MediaType.Image => "images"
        case MediaType.Video => "videos"
      val s3Key = s"$folder/${mediaId.value}/${req.filename}"

      PresignedUploadResponse(
        uploadUrl = s"$baseUrl/api/v1/dev/upload/$s3Key",
        mediaId = mediaId,
        s3Key = s3Key,
        expiresInS = 3600,
        publicUrl = publicUrl(s3Key)
      )
    }

  /** En desarrollo no hay firma ni expiración: devuelve la URL pública directamente. */
  def generatePresignedGetUrl(s3Key: String, expiresInSeconds: Int): IO[String] =
    IO.pure(publicUrl(s3Key))

  /** Elimina el archivo del disco local si existe. */
  def deleteObject(s3Key: String): IO[Unit] =
    IO.blocking {
      val path = Paths.get(uploadsDir, s3Key.split("/"): _*)
      Files.deleteIfExists(path)
      ()
    }

  def publicUrl(s3Key: String): String =
    s"$baseUrl/api/v1/dev/files/$s3Key"

object LocalStorageService:

  /** Crea el servicio y garantiza que el directorio de subidas existe. */
  def make(uploadsDir: String, baseUrl: String): IO[StorageService] =
    IO.blocking(Files.createDirectories(Paths.get(uploadsDir)))
      .as(new LocalStorageService(uploadsDir, baseUrl))
