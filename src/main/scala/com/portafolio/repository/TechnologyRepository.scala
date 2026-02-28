package com.portafolio.repository

import cats.effect.IO
import com.portafolio.domain.common.Ids.TechnologyId
import com.portafolio.domain.technology.Technology
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.util.UUID

/** Repositorio de tecnologías. */
trait TechnologyRepository:
  def findAll: IO[List[Technology]]
  def findById(id: TechnologyId): IO[Option[Technology]]
  def findByIds(ids: List[TechnologyId]): IO[List[Technology]]
  def create(name: String, iconUrl: Option[String]): IO[Technology]
  def update(id: TechnologyId, name: String, iconUrl: Option[String]): IO[Option[Technology]]
  def delete(id: TechnologyId): IO[Boolean]

object TechnologyRepository:

  def make(xa: Transactor[IO]): TechnologyRepository = new TechnologyRepository:


    private def toTech(id: UUID, name: String, iconUrl: Option[String]): Technology =
      Technology(TechnologyId(id), name, iconUrl)

    def findAll: IO[List[Technology]] =
      sql"SELECT id, name, icon_url FROM technologies ORDER BY name"
        .query[(UUID, String, Option[String])]
        .map(toTech.tupled)
        .to[List]
        .transact(xa)

    def findById(id: TechnologyId): IO[Option[Technology]] =
      sql"SELECT id, name, icon_url FROM technologies WHERE id = ${id.value}"
        .query[(UUID, String, Option[String])]
        .map(toTech.tupled)
        .option
        .transact(xa)

    def findByIds(ids: List[TechnologyId]): IO[List[Technology]] =
      if ids.isEmpty then IO.pure(List.empty)
      else
        val uuids = ids.map(_.value)
        (fr"SELECT id, name, icon_url FROM technologies WHERE id IN (" ++
          uuids.map(u => fr"$u").reduce(_ ++ fr"," ++ _) ++ fr")")
          .query[(UUID, String, Option[String])]
          .map(toTech.tupled)
          .to[List]
          .transact(xa)

    def create(name: String, iconUrl: Option[String]): IO[Technology] =
      sql"""
        INSERT INTO technologies (name, icon_url) VALUES ($name, $iconUrl)
        RETURNING id, name, icon_url
      """
        .query[(UUID, String, Option[String])]
        .map(toTech.tupled)
        .unique
        .transact(xa)

    def update(id: TechnologyId, name: String, iconUrl: Option[String]): IO[Option[Technology]] =
      sql"""
        UPDATE technologies SET name = $name, icon_url = $iconUrl
        WHERE id = ${id.value}
        RETURNING id, name, icon_url
      """
        .query[(UUID, String, Option[String])]
        .map(toTech.tupled)
        .option
        .transact(xa)

    def delete(id: TechnologyId): IO[Boolean] =
      sql"DELETE FROM technologies WHERE id = ${id.value}".update.run
        .map(_ > 0)
        .transact(xa)
