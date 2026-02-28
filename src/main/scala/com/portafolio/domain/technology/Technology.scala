package com.portafolio.domain.technology

import com.portafolio.domain.common.Ids.TechnologyId
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

/** Tecnología que puede asociarse a proyectos. */
final case class Technology(
    id:      TechnologyId,
    name:    String,
    iconUrl: Option[String]
)

final case class CreateTechnologyRequest(name: String, iconUrl: Option[String])
object CreateTechnologyRequest:
  given Encoder[CreateTechnologyRequest] = deriveEncoder
  given Decoder[CreateTechnologyRequest] = deriveDecoder
  given Schema[CreateTechnologyRequest]  = Schema.derived

final case class TechnologyResponse(
    id:      TechnologyId,
    name:    String,
    iconUrl: Option[String]
)
object TechnologyResponse:
  given Encoder[TechnologyResponse] = deriveEncoder
  given Decoder[TechnologyResponse] = deriveDecoder
  given Schema[TechnologyResponse]  = Schema.derived
