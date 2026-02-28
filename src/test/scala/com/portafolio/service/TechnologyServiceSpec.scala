package com.portafolio.service

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.portafolio.domain.common.Ids.TechnologyId
import com.portafolio.domain.common.errors.AppError
import com.portafolio.domain.technology.{CreateTechnologyRequest, Technology}
import com.portafolio.repository.TechnologyRepository
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.collection.mutable

class TechnologyServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers:

  private class InMemoryTechRepo extends TechnologyRepository:
    private val db = mutable.Map.empty[TechnologyId, Technology]

    def findAll: IO[List[Technology]] = IO.pure(db.values.toList)

    def findById(id: TechnologyId): IO[Option[Technology]] = IO.pure(db.get(id))

    def findByIds(ids: List[TechnologyId]): IO[List[Technology]] =
      IO.pure(ids.flatMap(db.get))

    def create(name: String, iconUrl: Option[String]): IO[Technology] =
      if db.values.exists(_.name == name) then IO.raiseError(new RuntimeException("Nombre duplicado"))
      else
        val t = Technology(TechnologyId.generate(), name, iconUrl)
        db += t.id -> t
        IO.pure(t)

    def update(id: TechnologyId, name: String, iconUrl: Option[String]): IO[Option[Technology]] =
      IO.pure(db.get(id).map { t =>
        val updated = t.copy(name = name, iconUrl = iconUrl)
        db += id -> updated
        updated
      })

    def delete(id: TechnologyId): IO[Boolean] =
      IO.pure(db.remove(id).isDefined)

  "TechnologyService" should {

    "crear una tecnología nueva" in {
      val repo = new InMemoryTechRepo
      val service = TechnologyService.make(repo)
      val req = CreateTechnologyRequest("Scala", Some("https://scala-lang.org/logo.png"))

      service.create(req).asserting {
        case Right(resp) =>
          resp.name shouldBe "Scala"
          resp.iconUrl shouldBe Some("https://scala-lang.org/logo.png")
        case Left(err) => fail(s"Error inesperado: $err")
      }
    }

    "rechazar nombre duplicado" in {
      val repo = new InMemoryTechRepo
      val service = TechnologyService.make(repo)
      val req = CreateTechnologyRequest("Scala", None)

      for
        _ <- service.create(req)
        result <- service.create(req)
      yield result match
        case Left(AppError.Conflict(_)) => succeed
        case other                      => fail(s"Resultado inesperado: $other")
    }

    "listar todas las tecnologías" in {
      val repo = new InMemoryTechRepo
      val service = TechnologyService.make(repo)

      for
        _ <- service.create(CreateTechnologyRequest("Scala", None))
        _ <- service.create(CreateTechnologyRequest("TypeScript", None))
        l <- service.listAll
      yield l should have length 2
    }
  }
