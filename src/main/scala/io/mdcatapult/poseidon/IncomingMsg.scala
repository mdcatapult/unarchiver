package io.mdcatapult.poseidon

import io.mdcatapult.klein.queue.Envelope
import play.api.libs.json.{Format, Json, Reads, Writes}


object IncomingMsg {
  implicit val msgReader: Reads[IncomingMsg] = Json.reads[IncomingMsg]
  implicit val msgWriter: Writes[IncomingMsg] = Json.writes[IncomingMsg]
  implicit val msgFormatter: Format[IncomingMsg] = Json.format[IncomingMsg]
}

case class IncomingMsg(id: String) extends Envelope


