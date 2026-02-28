package com.portafolio.domain.auth

import com.portafolio.domain.common.Ids.UserId
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

import java.time.Instant

/** Entidad de usuario almacenada en base de datos. */
final case class User(
    id:             UserId,
    email:          String,
    hashedPassword: String,
    createdAt:      Instant,
    updatedAt:      Instant
)

/** Credenciales recibidas en el endpoint de login. */
final case class LoginRequest(email: String, password: String)
object LoginRequest:
  given Encoder[LoginRequest] = deriveEncoder
  given Decoder[LoginRequest] = deriveDecoder
  given Schema[LoginRequest]  = Schema.derived

/** Respuesta exitosa del endpoint de login. */
final case class LoginResponse(token: String, expiresAt: Instant)
object LoginResponse:
  given Encoder[LoginResponse] = deriveEncoder
  given Decoder[LoginResponse] = deriveDecoder
  given Schema[LoginResponse]  = Schema.derived

/** Payload decodificado del JWT (usado en middleware). */
final case class AuthenticatedUser(id: UserId, email: String)
