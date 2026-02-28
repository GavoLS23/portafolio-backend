package com.portafolio.domain.common

import doobie.Meta
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

/** Idiomas soportados por el sistema de contenido multilingüe. */
enum Language(val code: String):
  case Es extends Language("es")
  case En extends Language("en")

object Language:
  def fromCode(code: String): Either[String, Language] =
    code.toLowerCase match
      case "es"  => Right(Language.Es)
      case "en"  => Right(Language.En)
      case other => Left(s"Idioma no soportado: '$other'. Use 'es' o 'en'.")

  given Encoder[Language] = Encoder[String].contramap(_.code)
  given Decoder[Language] = Decoder[String].emap(fromCode)
  given Meta[Language] = Meta[String].timap(s => fromCode(s).getOrElse(Language.Es))(_.code)
  given Schema[Language] = Schema.derivedEnumeration[Language].defaultStringBased
