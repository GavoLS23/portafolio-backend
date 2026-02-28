package com.portafolio.repository

import cats.effect.IO
import com.portafolio.domain.auth.User
import com.portafolio.domain.common.Ids.UserId
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.time.Instant
import java.util.UUID

/** Repositorio de usuarios (CRUD sobre la tabla `users`). */
trait UserRepository:
  def findById(id: UserId): IO[Option[User]]
  def findByEmail(email: String): IO[Option[User]]
  def create(email: String, hashedPassword: String): IO[User]
  def existsAny: IO[Boolean]

object UserRepository:

  def make(xa: Transactor[IO]): UserRepository = new UserRepository:

    // ── Doobie Meta instances ────────────────────────────────────────────────

    private def toUser(
        id: UUID,
        email: String,
        hp: String,
        ca: Instant,
        ua: Instant
    ): User =
      User(
        id = UserId(id),
        email = email,
        hashedPassword = hp,
        createdAt = ca,
        updatedAt = ua
      )

    def findById(id: UserId): IO[Option[User]] =
      sql"""
        SELECT id, email, hashed_password, created_at, updated_at
        FROM users WHERE id = ${id.value}
      """
        .query[(UUID, String, String, Instant, Instant)]
        .map(toUser.tupled)
        .option
        .transact(xa)

    def findByEmail(email: String): IO[Option[User]] =
      sql"""
        SELECT id, email, hashed_password, created_at, updated_at
        FROM users WHERE email = $email
      """
        .query[(UUID, String, String, Instant, Instant)]
        .map(toUser.tupled)
        .option
        .transact(xa)

    def create(email: String, hashedPassword: String): IO[User] =
      sql"""
        INSERT INTO users (email, hashed_password)
        VALUES ($email, $hashedPassword)
        RETURNING id, email, hashed_password, created_at, updated_at
      """
        .query[(UUID, String, String, Instant, Instant)]
        .map(toUser.tupled)
        .unique
        .transact(xa)

    def existsAny: IO[Boolean] =
      sql"SELECT EXISTS (SELECT 1 FROM users LIMIT 1)"
        .query[Boolean]
        .unique
        .transact(xa)
