package com.portafolio.infrastructure.aws

import cats.effect.{IO, Resource}
import com.portafolio.config.AwsConfig
import com.portafolio.domain.common.Ids.MediaId
import com.portafolio.domain.media.{MediaType, PresignedUploadRequest, PresignedUploadResponse}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{DeleteObjectRequest, GetObjectRequest}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.{GetObjectPresignRequest, PutObjectPresignRequest}
import software.amazon.awssdk.services.s3.model.PutObjectRequest

import java.time.Duration

/** Servicio de integración con AWS S3.
  *
  * Genera URLs pre-firmadas para que el frontend suba archivos directamente a S3 sin pasar por el servidor (upload directo).
  */
trait S3Service:
  /** Nombre del bucket S3 configurado. */
  def bucket: String

  /** Genera una URL pre-firmada de PUT para subir un archivo a S3. */
  def generatePresignedPutUrl(req: PresignedUploadRequest): IO[PresignedUploadResponse]

  /** Genera una URL pre-firmada de GET para servir un archivo privado. */
  def generatePresignedGetUrl(s3Key: String, expiresInSeconds: Int): IO[String]

  /** Elimina un archivo de S3. */
  def deleteObject(s3Key: String): IO[Unit]

  /** Construye la URL pública de un objeto (si el bucket es público). */
  def publicUrl(s3Key: String): String

object S3Service:

  def make(config: AwsConfig): Resource[IO, S3Service] =
    val credentials = StaticCredentialsProvider.create(
      AwsBasicCredentials.create(
        config.accessKeyId.value,
        config.secretAccessKey.value
      )
    )
    val region = Region.of(config.region)

    Resource
      .fromAutoCloseable(
        IO.blocking(
          S3Presigner
            .builder()
            .region(region)
            .credentialsProvider(credentials)
            .build()
        )
      )
      .map { presigner =>
        new S3Service:
          def bucket: String = config.s3Bucket

          def generatePresignedPutUrl(req: PresignedUploadRequest): IO[PresignedUploadResponse] =
            IO.blocking {
              val mediaId = MediaId.generate()
              val folder = req.mediaType match
                case MediaType.Image => "images"
                case MediaType.Video => "videos"
              val s3Key = s"$folder/${mediaId.value}/${req.filename}"

              val putObjectRequest = PutObjectRequest
                .builder()
                .bucket(config.s3Bucket)
                .key(s3Key)
                .contentType(req.mimeType)
                .build()

              val presignRequest = PutObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putObjectRequest)
                .build()

              val presignedUrl = presigner.presignPutObject(presignRequest)

              PresignedUploadResponse(
                uploadUrl = presignedUrl.url().toString,
                mediaId = mediaId,
                s3Key = s3Key,
                expiresInS = 600
              )
            }

          def generatePresignedGetUrl(s3Key: String, expiresInSeconds: Int): IO[String] =
            IO.blocking {
              val getObjectRequest = GetObjectRequest
                .builder()
                .bucket(config.s3Bucket)
                .key(s3Key)
                .build()

              val presignRequest = GetObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofSeconds(expiresInSeconds.toLong))
                .getObjectRequest(getObjectRequest)
                .build()

              presigner.presignGetObject(presignRequest).url().toString
            }

          def deleteObject(s3Key: String): IO[Unit] =
            Resource
              .fromAutoCloseable(
                IO.blocking(
                  S3Client
                    .builder()
                    .region(region)
                    .credentialsProvider(credentials)
                    .build()
                )
              )
              .use { s3 =>
                IO.blocking {
                  s3.deleteObject(
                    DeleteObjectRequest
                      .builder()
                      .bucket(config.s3Bucket)
                      .key(s3Key)
                      .build()
                  )
                }.void
              }

          def publicUrl(s3Key: String): String =
            s"https://${config.s3Bucket}.s3.${config.region}.amazonaws.com/$s3Key"
      }
