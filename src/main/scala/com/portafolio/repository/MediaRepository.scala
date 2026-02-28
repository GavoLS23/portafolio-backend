package com.portafolio.repository

import cats.effect.IO
import com.portafolio.domain.common.Ids.MediaId
import com.portafolio.domain.media.{Media, MediaType}
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.time.Instant
import java.util.UUID

/** Repositorio de metadata de media (las propias imágenes/videos están en S3). */
trait MediaRepository:
  def findById(id: MediaId): IO[Option[Media]]
  def findAll(limit: Int, offset: Int): IO[List[Media]]
  def countAll: IO[Long]
  def create(
      mediaId: MediaId,
      s3Key: String,
      s3Bucket: String,
      filename: String,
      mimeType: String,
      mediaType: MediaType,
      sizeBytes: Long
  ): IO[Media]
  def confirmUpload(
      id: MediaId,
      widthPx: Option[Int],
      heightPx: Option[Int],
      durationS: Option[Int]
  ): IO[Option[Media]]
  def delete(id: MediaId): IO[Option[String]]

object MediaRepository:

  def make(xa: Transactor[IO]): MediaRepository = new MediaRepository:

    // Doobie Meta para MediaType
    given Meta[MediaType] = Meta[String].timap(s => MediaType.fromString(s).getOrElse(MediaType.Image))(_.value)

    private type MediaRow = (UUID, String, String, String, String, String, Long, Option[Int], Option[Int], Option[Int], Instant)

    private def toMedia(row: MediaRow): Media =
      val (id, s3Key, s3Bucket, filename, mimeType, mt, sizeBytes, w, h, d, ca) = row
      Media(
        id = MediaId(id),
        s3Key = s3Key,
        s3Bucket = s3Bucket,
        filename = filename,
        mimeType = mimeType,
        mediaType = MediaType.fromString(mt).getOrElse(MediaType.Image),
        sizeBytes = sizeBytes,
        widthPx = w,
        heightPx = h,
        durationS = d,
        createdAt = ca
      )

    private val selectAll =
      fr"""SELECT id, s3_key, s3_bucket, filename, mime_type, media_type::text,
                  size_bytes, width_px, height_px, duration_s, created_at
           FROM media"""

    def findById(id: MediaId): IO[Option[Media]] =
      (selectAll ++ fr"WHERE id = ${id.value}")
        .query[MediaRow]
        .map(toMedia)
        .option
        .transact(xa)

    def findAll(limit: Int, offset: Int): IO[List[Media]] =
      (selectAll ++ fr"ORDER BY created_at DESC LIMIT $limit OFFSET $offset")
        .query[MediaRow]
        .map(toMedia)
        .to[List]
        .transact(xa)

    def countAll: IO[Long] =
      sql"SELECT COUNT(*) FROM media"
        .query[Long]
        .unique
        .transact(xa)

    def create(
        mediaId: MediaId,
        s3Key: String,
        s3Bucket: String,
        filename: String,
        mimeType: String,
        mediaType: MediaType,
        sizeBytes: Long
    ): IO[Media] =
      sql"""
        INSERT INTO media (id, s3_key, s3_bucket, filename, mime_type, media_type, size_bytes)
        VALUES (${mediaId.value}, $s3Key, $s3Bucket, $filename, $mimeType,
                ${mediaType.value}::media_type, $sizeBytes)
        RETURNING id, s3_key, s3_bucket, filename, mime_type, media_type::text,
                  size_bytes, width_px, height_px, duration_s, created_at
      """
        .query[MediaRow]
        .map(toMedia)
        .unique
        .transact(xa)

    def confirmUpload(id: MediaId, widthPx: Option[Int], heightPx: Option[Int], durationS: Option[Int]): IO[Option[Media]] =
      sql"""
        UPDATE media SET width_px = $widthPx, height_px = $heightPx, duration_s = $durationS
        WHERE id = ${id.value}
        RETURNING id, s3_key, s3_bucket, filename, mime_type, media_type::text,
                  size_bytes, width_px, height_px, duration_s, created_at
      """
        .query[MediaRow]
        .map(toMedia)
        .option
        .transact(xa)

    def delete(id: MediaId): IO[Option[String]] =
      sql"""
        DELETE FROM media WHERE id = ${id.value}
        RETURNING s3_key
      """
        .query[String]
        .option
        .transact(xa)
