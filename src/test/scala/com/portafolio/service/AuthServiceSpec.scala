package com.portafolio.service

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.portafolio.config.JwtConfig
import com.portafolio.domain.auth.{LoginRequest, User}
import com.portafolio.domain.common.Ids.UserId
import com.portafolio.domain.common.errors.AppError
import com.portafolio.repository.UserRepository
import ciris.Secret
import org.mindrot.jbcrypt.BCrypt
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger  // Import correcto

import java.time.Instant
import java.util.UUID

class AuthServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers:

  given logger: Logger[IO] = NoOpLogger[IO]()  // Usando el de log4cats

  private val testConfig = JwtConfig(
    secret          = Secret("test-secret-key-min-32-chars-long"),
    expirationHours = 24L
  )

  private val testUserId = UserId(UUID.randomUUID())
  private val testEmail  = "admin@test.com"
  private val rawPass    = "secret123"
  private val hashedPass = BCrypt.hashpw(rawPass, BCrypt.gensalt(4))

  private val testUser = User(
    id             = testUserId,
    email          = testEmail,
    hashedPassword = hashedPass,
    createdAt      = Instant.now,
    updatedAt      = Instant.now
  )

  private val userRepo = new UserRepository:
    def findById(id: UserId): IO[Option[User]]          = IO.pure(if id == testUserId then Some(testUser) else None)
    def findByEmail(email: String): IO[Option[User]]    = IO.pure(if email == testEmail then Some(testUser) else None)
    def create(email: String, hp: String): IO[User]     = IO.pure(testUser)
    def existsAny: IO[Boolean]                          = IO.pure(true)

  private val authService = AuthService.make(userRepo, testConfig)

  "AuthService.login" should {

    "devolver token JWT con credenciales válidas" in {
      authService.login(LoginRequest(testEmail, rawPass)).asserting {
        case Right(res) =>
          res.token should not be empty
          res.expiresAt should be > Instant.now
        case Left(err) => fail(s"Login falló: $err")
      }
    }

    "rechazar contraseña incorrecta" in {
      authService.login(LoginRequest(testEmail, "wrong")).asserting {
        _ shouldBe Left(AppError.Unauthorized("Credenciales inválidas"))
      }
    }

    "rechazar email inexistente" in {
      authService.login(LoginRequest("noexiste@test.com", rawPass)).asserting {
        _ shouldBe Left(AppError.Unauthorized("Credenciales inválidas"))
      }
    }
  }

  "AuthService.validateToken" should {

    "validar un token generado correctamente" in {
      for
        loginResult <- authService.login(LoginRequest(testEmail, rawPass))
        token        = loginResult.getOrElse(fail("Login falló")).token
        validation  <- authService.validateToken(token)
      yield validation match
        case Right(user) =>
          user.email shouldBe testEmail
        case Left(err) => fail(s"Validación falló: $err")
    }

    "rechazar un token inválido" in {
      authService.validateToken("token.invalido.xxx").asserting {
        case Left(AppError.Unauthorized(_)) => succeed
        case other                          => fail(s"Resultado inesperado: $other")
      }
    }
  }