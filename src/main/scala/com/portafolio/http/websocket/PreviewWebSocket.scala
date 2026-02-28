package com.portafolio.http.websocket

import cats.effect.IO
import cats.effect.std.Queue
import com.portafolio.service.AuthService
import fs2.{Pipe, Stream}
import io.circe.syntax.*
import io.circe.{Encoder, Json}
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

/** Mensajes del protocolo WebSocket para previsualización en vivo y autosave. */
sealed trait WsMessage
object WsMessage:
  case class PreviewUpdate(entityType: String, entityId: String, data: Json) extends WsMessage
  case class AutosaveAck(entityId: String, savedAt: String) extends WsMessage
  case class Error(message: String) extends WsMessage

  given Encoder[WsMessage] = Encoder.instance {
    case PreviewUpdate(t, id, d) =>
      Json.obj("type" -> "preview_update".asJson, "entityType" -> t.asJson, "entityId" -> id.asJson, "data" -> d)
    case AutosaveAck(id, at) =>
      Json.obj("type" -> "autosave_ack".asJson, "entityId" -> id.asJson, "savedAt" -> at.asJson)
    case Error(msg) =>
      Json.obj("type" -> "error".asJson, "message" -> msg.asJson)
  }

/** Ruta WebSocket para previsualización en tiempo real.
  *
  * Endpoint: `GET /ws/preview?token=<jwt>`
  *
  * El token va como query param porque los WebSockets del navegador no soportan headers personalizados en el handshake inicial.
  */
object PreviewWebSocket:

  def routes(
      authService: AuthService,
      wsBuilder: WebSocketBuilder2[IO]
  )(using logger: Logger[IO]): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root / "ws" / "preview" :? TokenParam(token) =>
      authService.validateToken(token).flatMap {
        case Left(_) =>
          Forbidden()

        case Right(user) =>
          Queue.unbounded[IO, WebSocketFrame].flatMap { outQueue =>

            // Stream de mensajes hacia el cliente
            val send: Stream[IO, WebSocketFrame] =
              Stream
                .fromQueueUnterminated(outQueue)
                .merge(
                  // Ping cada 30s para mantener viva la conexión
                  Stream.awakeEvery[IO](30.seconds).map(_ => WebSocketFrame.Ping())
                )

            // Pipe de mensajes desde el cliente
            val receive: Pipe[IO, WebSocketFrame, Unit] = frames =>
              Stream.eval(logger.info(s"WS conectado: ${user.email}")) ++
                frames.evalMap {
                  case Text(msg, _) =>
                    io.circe.parser.parse(msg).flatMap(_.hcursor.get[String]("type")) match
                      case Right("autosave") =>
                        val entityId = io.circe.parser
                          .parse(msg)
                          .flatMap(_.hcursor.get[String]("entityId"))
                          .getOrElse("unknown")
                        val ack: WsMessage = WsMessage.AutosaveAck(
                          entityId = entityId,
                          savedAt = java.time.Instant.now.toString
                        )
                        outQueue.offer(Text(ack.asJson.noSpaces))
                      case _ =>
                        IO.unit

                  case Close(_) =>
                    logger.info(s"WS desconectado: ${user.email}")

                  case _ =>
                    IO.unit
                }

            wsBuilder.build(send, receive)
          }
      }
    }

  private object TokenParam extends QueryParamDecoderMatcher[String]("token")
