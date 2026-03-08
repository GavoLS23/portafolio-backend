package com.portafolio.infrastructure.aws

import cats.effect.{IO, Resource}
import com.portafolio.config.AwsConfig
import com.portafolio.domain.common.Ids.MediaId
import com.portafolio.domain.media.{MediaType, PresignedUploadRequest, PresignedUploadResponse}
import com.portafolio.infrastructure.storage.StorageService
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{DeleteObjectRequest, GetObjectRequest, ObjectCannedACL, PutObjectRequest}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.{GetObjectPresignRequest, PutObjectPresignRequest}

import java.time.Duration

/** Implementación de [[StorageService]] para el ambiente de producción con AWS S3.
  *
  * Usa URLs pre-firmadas para que el frontend suba archivos directamente a S3 sin que el tráfico pase por el servidor (upload directo).
  *
  * Expiración de URLs de subida: 10 minutos.
  */
object S3Service:

  /** Crea el cliente S3 y el presigner. El Resource cierra el presigner al finalizar. */
  def make(config: AwsConfig): Resource[IO, StorageService] =
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
        new StorageService:

          def bucket: String = config.s3Bucket

          /** Genera URL pre-firmada de PUT con validez de 10 minutos. */
          def generatePresignedPutUrl(req: PresignedUploadRequest): IO[PresignedUploadResponse] =
            IO.blocking {
              val mediaId = MediaId.generate()
              val folder = req.mediaType match
                case MediaType.Image => "images"
                case MediaType.Video => "videos"
              val s3Key = s"$folder/${mediaId.value}/${req.filename}"

              val putReq = PutObjectRequest
                .builder()
                .bucket(config.s3Bucket)
                .key(s3Key)
                .contentType(req.mimeType)
                // El objeto será público al momento de la subida.
                // NOTA: el bucket debe tener "Block Public Access for ACLs" deshabilitado.
                // Para migrar a CloudFront (bucket privado + OAC), eliminar esta línea y
                // construir la publicUrl con el dominio de la distribución CloudFront.
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build()

              val presignReq = PutObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putReq)
                .build()

              val presignedUrl = presigner.presignPutObject(presignReq)

              PresignedUploadResponse(
                uploadUrl = presignedUrl.url().toString,
                mediaId = mediaId,
                s3Key = s3Key,
                expiresInS = 600,
                publicUrl = publicUrl(s3Key)
              )
            }

          /** Genera URL pre-firmada de GET con la expiración indicada. */
          def generatePresignedGetUrl(s3Key: String, expiresInSeconds: Int): IO[String] =
            IO.blocking {
              val getReq = GetObjectRequest
                .builder()
                .bucket(config.s3Bucket)
                .key(s3Key)
                .build()

              val presignReq = GetObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofSeconds(expiresInSeconds.toLong))
                .getObjectRequest(getReq)
                .build()

              presigner.presignGetObject(presignReq).url().toString
            }

          /** Elimina el objeto de S3 abriendo un S3Client efímero. */
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
