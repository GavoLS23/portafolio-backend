package com.portafolio.http.middleware

import cats.effect.IO
import com.portafolio.domain.auth.AuthenticatedUser
import com.portafolio.domain.common.errors.{AppError, ErrorResponse}
import com.portafolio.service.AuthService
import sttp.model.StatusCode
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*

/** Helpers de autenticación para los endpoints de Tapir.
  *
  * Patrón de uso en cada endpoint protegido:
  * {{{
  *   myEndpoint
  *     .serverSecurityLogic(AuthMiddleware.securityLogic(authService))
  *     .serverLogic { user => input => ... }
  * }}}
  */
object AuthMiddleware:

  /** Tipo de error que usan todos los endpoints: (StatusCode, ErrorResponse). */
  type TapirError = (StatusCode, ErrorResponse)

  /** Convierte un AppError de dominio al formato de error de Tapir. */
  def toTapirError(err: AppError): TapirError =
    (err.httpStatus, ErrorResponse.from(err))

  /** Función de seguridad reutilizable: valida el token Bearer y devuelve
    * el usuario autenticado, o un error Tapir si el token es inválido.
    */
  def securityLogic(
      authService: AuthService
  ): String => IO[Either[TapirError, AuthenticatedUser]] =
    token =>
      authService
        .validateToken(token)
        .map(_.left.map(toTapirError))
