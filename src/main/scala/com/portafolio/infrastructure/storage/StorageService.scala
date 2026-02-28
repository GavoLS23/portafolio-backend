package com.portafolio.infrastructure.storage

import cats.effect.IO
import com.portafolio.domain.media.{PresignedUploadRequest, PresignedUploadResponse}

/** Abstracción de almacenamiento de archivos binarios.
  *
  * Permite intercambiar la implementación según el ambiente:
  *   - [[LocalStorageService]] → guarda en disco, pensado para desarrollo
  *   - [[S3StorageService]] → sube a AWS S3, pensado para producción
  *
  * El flujo de subida es siempre el mismo desde el punto de vista del dominio:
  *   1. El frontend solicita una URL de subida (`generatePresignedPutUrl`). 2. El frontend hace `PUT` directo a esa URL (sin pasar por el servidor). 3. El frontend confirma la subida al backend para
  *      que se persista la metadata.
  */
trait StorageService:

  /** Identificador lógico del almacenamiento (bucket S3 o directorio local). */
  def bucket: String

  /** Genera una URL de PUT para que el frontend suba un archivo directamente.
    *
    * En producción devuelve una URL pre-firmada de S3 con expiración de 10 minutos. En desarrollo devuelve una URL que apunta al propio backend (ruta `/dev/upload/…`).
    */
  def generatePresignedPutUrl(req: PresignedUploadRequest): IO[PresignedUploadResponse]

  /** Genera una URL de GET para acceder a un archivo privado.
    *
    * En producción la URL tiene firma y expira tras `expiresInSeconds`. En desarrollo devuelve directamente la URL pública del servidor local.
    */
  def generatePresignedGetUrl(s3Key: String, expiresInSeconds: Int): IO[String]

  /** Elimina un archivo del almacenamiento (S3 o disco local). */
  def deleteObject(s3Key: String): IO[Unit]

  /** Construye la URL pública permanente de un archivo. */
  def publicUrl(s3Key: String): String
