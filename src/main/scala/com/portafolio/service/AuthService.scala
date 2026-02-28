package com.portafolio.service

import cats.effect.IO
import com.portafolio.config.JwtConfig
import com.portafolio.domain.auth.{AuthenticatedUser, LoginRequest, LoginResponse, User}
import com.portafolio.domain.common.Ids.UserId
import com.portafolio.domain.common.errors.AppError
import com.portafolio.repository.UserRepository
import org.mindrot.jbcrypt.BCrypt
import org.typelevel.log4cats.Logger
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import java.time.Instant
import scala.util.Try

/** Servicio de autenticación: login, validación de JWT y seed del admin inicial. */
trait AuthService:
  /** Valida credenciales y devuelve un token JWT. */
  def login(req: LoginRequest): IO[Either[AppError, LoginResponse]]

  /** Valida un token JWT y devuelve el usuario autenticado. */
  def validateToken(token: String): IO[Either[AppError, AuthenticatedUser]]

  /** Crea el usuario administrador si la BD está vacía. */
  def seedAdmin(email: String, rawPassword: String): IO[Unit]

object AuthService:

  def make(
      userRepo: UserRepository,
      config:   JwtConfig
  )(using logger: Logger[IO]): AuthService = new AuthService:

    private val algorithm = JwtAlgorithm.HS256

    def login(req: LoginRequest): IO[Either[AppError, LoginResponse]] =
      userRepo.findByEmail(req.email).map {
        case None =>
          Left(AppError.Unauthorized("Credenciales inválidas"))
        case Some(user) if !BCrypt.checkpw(req.password, user.hashedPassword) =>
          Left(AppError.Unauthorized("Credenciales inválidas"))
        case Some(user) =>
          val expiresAt = Instant.now.plusSeconds(config.expirationHours * 3600)
          val claim = JwtClaim(
            subject    = Some(user.id.value.toString),
            content    = s"""{"email":"${user.email}"}""",
            expiration = Some(expiresAt.getEpochSecond),
            issuedAt   = Some(Instant.now.getEpochSecond)
          )
          val token = Jwt.encode(claim, config.secret.value, algorithm)
          Right(LoginResponse(token = token, expiresAt = expiresAt))
      }

    def validateToken(token: String): IO[Either[AppError, AuthenticatedUser]] =
      IO.fromTry(
        Jwt.decode(token, config.secret.value, Seq(algorithm))
      ).map { claim =>
        (for
          sub   <- claim.subject.toRight("Token sin subject")
          uuid  <- Try(java.util.UUID.fromString(sub)).toEither.left.map(_.getMessage)
          email <- io.circe.parser
                     .parse(claim.content)
                     .flatMap(_.hcursor.get[String]("email"))
                     .left.map(_.getMessage)
        yield AuthenticatedUser(UserId(uuid), email))
          .left.map(msg => AppError.Unauthorized(msg))
      }.recover {
        case _: Exception => Left(AppError.Unauthorized("Token inválido o expirado"))
      }

    def seedAdmin(email: String, rawPassword: String): IO[Unit] =
      userRepo.existsAny.flatMap {
        case true  => logger.info("Admin ya existe, omitiendo seed")
        case false =>
          val hashed = BCrypt.hashpw(rawPassword, BCrypt.gensalt(12))
          userRepo.create(email, hashed).flatMap { user =>
            logger.info(s"Admin inicial creado: ${user.email}")
          }
      }
