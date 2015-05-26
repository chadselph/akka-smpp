package akkasmpp.protocol

import akka.stream.stage.{SyncDirective, Context, StageState, StatefulStage}
import akka.util.ByteString

import scala.annotation.tailrec

/**
 * Stateful parser for reading PDUs from TCP data
 */
class SmppPduParsingStage extends StatefulStage[ByteString, Pdu] {

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
      // let's get at least a header before proceeding...
      if (!(bs.length > 4 && bs.length >= bs.iterator.getInt)) {
        (bs, acc)
      } else {
        val commandLength = bs.iterator.getInt
        if (bs.size >= commandLength) {
          val (pdu, extra) = bs.splitAt(commandLength)
          val parsed = Pdu.fromBytes(pdu.iterator)
          pduFromBuffer(extra, parsed :: acc)
        } else (bs, acc)
      }
    }
  }
}
