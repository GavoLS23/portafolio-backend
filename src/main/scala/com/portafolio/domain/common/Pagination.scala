package com.portafolio.domain.common

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

/** Parámetros de paginación para listados. */
final case class Pagination(page: Int, pageSize: Int):
  def offset: Int = (page - 1) * pageSize
  def limit: Int = pageSize

object Pagination:
  val default: Pagination = Pagination(page = 1, pageSize = 20)

  given Encoder[Pagination] = deriveEncoder
  given Decoder[Pagination] = deriveDecoder
  given Schema[Pagination] = Schema.derived

/** Respuesta paginada genérica. */
final case class Page[A](
    items: List[A],
    total: Long,
    page: Int,
    pageSize: Int,
    totalPages: Int
)

object Page:
  def of[A](items: List[A], total: Long, pagination: Pagination): Page[A] =
    Page(
      items = items,
      total = total,
      page = pagination.page,
      pageSize = pagination.pageSize,
      totalPages = Math.ceil(total.toDouble / pagination.pageSize).toInt
    )
