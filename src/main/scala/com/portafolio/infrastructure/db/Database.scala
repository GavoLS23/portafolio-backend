package com.portafolio.infrastructure.db

import cats.effect.{IO, Resource}
import com.portafolio.config.DbConfig
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway
import org.typelevel.log4cats.Logger

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

/** Gestiona el pool de conexiones PostgreSQL y las migraciones Flyway. */
object Database:

  /** Crea un transactor HikariCP configurado para usar el schema `config.schema`.
    *
    * El `connectionInitSql` establece `search_path` en cada conexión nueva del pool, de modo que todas las queries de Doobie operan sobre el schema correcto sin necesidad de calificar los nombres de
    * tabla.
    *
    * @param config
    *   Configuración de base de datos.
    * @return
    *   Resource que libera el pool al finalizar.
    */
  def makeTransactor(config: DbConfig): Resource[IO, HikariTransactor[IO]] =
    for
      ec <- Resource.eval(
        IO(
          ExecutionContext.fromExecutorService(
            Executors.newFixedThreadPool(config.poolSize)
          )
        )
      )
      xa <- HikariTransactor.newHikariTransactor[IO](
        driverClassName = "org.postgresql.Driver",
        url = config.url,
        user = config.user,
        pass = config.password.value,
        connectEC = ec
      )
      // Establecer search_path para cada conexión del pool
      _ <- Resource.eval(
        xa.configure(ds => IO(ds.setConnectionInitSql(s"SET search_path TO ${config.schema}")))
      )
    yield xa

  /** Ejecuta las migraciones Flyway de forma síncrona al iniciar.
    *
    * `.schemas(config.schema)` instruye a Flyway para:
    *   1. Crear el schema si no existe (`CREATE SCHEMA IF NOT EXISTS portafolio`) 2. Almacenar `flyway_schema_history` dentro de ese schema 3. Establecer `search_path` durante la ejecución de cada
    *      migración
    *
    * Los scripts SQL en `db/migration/` no necesitan calificar los nombres de tabla con el schema porque Flyway gestiona el `search_path`.
    */
  def migrate(config: DbConfig)(using logger: Logger[IO]): IO[Unit] =
    IO.blocking {
      Flyway
        .configure()
        .dataSource(config.url, config.user, config.password.value)
        .schemas(config.schema)
        .defaultSchema(config.schema)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .load()
        .migrate()
    }.flatMap { result =>
      logger.info(
        s"Flyway: ${result.migrationsExecuted} migraciones ejecutadas " +
          s"en schema '${config.schema}'"
      )
    }
