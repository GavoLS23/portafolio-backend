package com.portafolio.http.routes

import cats.effect.IO
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.io.*

import java.nio.file.{Files, Paths}
import fs2.io.file.{Path => Fs2Path}

/** Rutas HTTP exclusivas del ambiente de desarrollo para gestionar archivos locales.
  *
  * Estas rutas NO se montan en producción. Permiten simular el flujo de upload directo a S3 usando el propio servidor como destino de subida:
  *
  *   - `PUT /api/v1/dev/upload/{folder}/{uuid}/{filename}` — recibe el archivo y lo guarda en disco.
  *   - `GET /api/v1/dev/files/{folder}/{uuid}/{filename}` — sirve el archivo desde disco.
  *
  * La estructura de paths espeja la usada en S3: `{tipo}/{uuid}/{nombre-original}`, por ejemplo: `images/550e8400-e29b-41d4-a716-446655440000/foto.jpg`.
  *
  * @param uploadsDir
  *   Directorio raíz donde se almacenan los archivos (e.g. `./uploads`).
  */
object LocalUploadRoutes:

  def make(uploadsDir: String): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      // ── Subida de archivo ───────────────────────────────────────────────────
      // El frontend hace PUT con el binario en el cuerpo, tal como haría con S3.
      case req @ PUT -> Root / "api" / "v1" / "dev" / "upload" / folder / uuid / filename =>
        val filePath = Paths.get(uploadsDir, folder, uuid, filename)
        for
          _ <- IO.blocking(Files.createDirectories(filePath.getParent))
          body <- req.body.compile.toVector
          _ <- IO.blocking(Files.write(filePath, body.toArray))
          resp <- NoContent()
        yield resp

      // ── Descarga / servicio de archivo ────────────────────────────────────
      // Sirve el archivo guardado previamente.
      case req @ GET -> Root / "api" / "v1" / "dev" / "files" / folder / uuid / filename =>
        val filePath = Paths.get(uploadsDir, folder, uuid, filename)
        StaticFile
          .fromPath(Fs2Path.fromNioPath(filePath), Some(req))
          .getOrElseF(NotFound())
    }
