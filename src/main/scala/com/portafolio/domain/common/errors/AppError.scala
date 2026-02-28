package com.portafolio.domain.common.errors

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.model.StatusCode
import sttp.tapir.Schema

/** Errores de dominio que se propagan a través de la aplicación.
  * Cada variante incluye el StatusCode HTTP correspondiente.
  */
sealed trait AppError:
  def message: String
  def httpStatus: StatusCode

object AppError:
  /** Recurso no encontrado (404). */
  final case class NotFound(message: String) extends AppError:
    val httpStatus: StatusCode = StatusCode.NotFound

  /** Credenciales inválidas o token expirado (401). */
  final case class Unauthorized(message: String) extends AppError:
    val httpStatus: StatusCode = StatusCode.Unauthorized

  /** El usuario no tiene permisos suficientes (403). */
  final case class Forbidden(message: String) extends AppError:
    val httpStatus: StatusCode = StatusCode.Forbidden

  /** Datos de entrada inválidos (400). */
  final case class BadRequest(message: String) extends AppError:
    val httpStatus: StatusCode = StatusCode.BadRequest

  /** Conflicto con el estado actual (ej. slug duplicado) (409). */
  final case class Conflict(message: String) extends AppError:
    val httpStatus: StatusCode = StatusCode.Conflict

  /** Error interno del servidor (500). */
  final case class InternalError(message: String) extends AppError:
    val httpStatus: StatusCode = StatusCode.InternalServerError

/** DTO de error devuelto en las respuestas HTTP. */
final case class ErrorResponse(error: String, message: String, statusCode: Int)

object ErrorResponse:
  def from(e: AppError): ErrorResponse =
    ErrorResponse(
      error      = e.getClass.getSimpleName,
      message    = e.message,
      statusCode = e.httpStatus.code
    )

  given Encoder[ErrorResponse] = deriveEncoder
  given Decoder[ErrorResponse] = deriveDecoder
  given Schema[ErrorResponse]  = Schema.derived
