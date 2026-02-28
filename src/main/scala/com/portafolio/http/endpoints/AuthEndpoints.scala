package com.portafolio.http.endpoints

import cats.effect.IO
import com.portafolio.domain.auth.{LoginRequest, LoginResponse}
import com.portafolio.domain.common.errors.ErrorResponse
import com.portafolio.service.AuthService
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

/** Endpoints de autenticación (públicos). */
object AuthEndpoints:

  private val base =
    endpoint
      .tag("Auth")
      .in("api" / "v1" / "auth")
      .errorOut(statusCode.and(jsonBody[ErrorResponse]))

  /** POST /api/v1/auth/login — Obtiene un token JWT. */
  private val loginEndpoint =
    base
      .summary("Login del administrador")
      .description("Valida credenciales y devuelve un token JWT Bearer.")
      .post
      .in("login")
      .in(jsonBody[LoginRequest])
      .out(jsonBody[LoginResponse])

  def serverEndpoints(authService: AuthService): List[ServerEndpoint[Any, IO]] =
    List(
      loginEndpoint.serverLogic { req =>
        authService.login(req).map {
          case Right(res) => Right(res)
          case Left(err)  => Left((err.httpStatus, ErrorResponse.from(err)))
        }
      }
    )
