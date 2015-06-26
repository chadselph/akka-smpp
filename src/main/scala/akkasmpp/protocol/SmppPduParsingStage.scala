package akkasmpp.protocol

import akka.stream.stage.{Context, StageState, StatefulStage, SyncDirective}
import akka.util.ByteString
import com.typesafe.config.Config

import scala.annotation.tailrec

/**
 * Stateful parser for reading PDUs from TCP data
 */
class SmppPduParsingStage(config: Config) extends StatefulStage[ByteString, Pdu] {

  var buffer = ByteString.empty
  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

  override def initial: StageState[ByteString, Pdu] = new State {
    override def onPush(elem: ByteString, ctx: Context[Pdu]): SyncDirective = {
      val (buf, pdus) = pduFromBuffer(buffer ++ elem, List.empty)
      buffer = buf
      emit(pdus.iterator, ctx)
    }

    @tailrec
    def pduFromBuffer(bs: ByteString, acc: List[Pdu]): (ByteString, Seq[Pdu]) = {
      if (bs.length < 4) {
        // we don't even have enough to read the length
        (bs, acc)
      } else {
        val commandLength = bs.iterator.getInt
        if (commandLength > config.getInt("protocol.max-pdu-length") ||
            commandLength < config.getInt("protocol.min-pdu-length")) {
          throw new Exception("Invalid command length.")
        } else if (bs.length < commandLength) {
          (bs, acc)
        } else {
          val (pdu, extra) = bs.splitAt(commandLength)
          val parsed = Pdu.fromBytes(pdu.iterator)
          pduFromBuffer(extra, parsed :: acc)
        }
      }
    }
  }
}
