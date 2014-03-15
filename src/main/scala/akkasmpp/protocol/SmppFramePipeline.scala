package akkasmpp.protocol

import akka.io.{SymmetricPipePair, PipelineContext, SymmetricPipelineStage}
import akka.util.ByteString
import java.nio.ByteOrder
import scala.annotation.tailrec

/**
 * Decodes bytestrings into PDUs, vice versa.
 */
class SmppFramePipeline extends SymmetricPipelineStage[PipelineContext, Pdu, ByteString] {

  implicit val charencoding = java.nio.charset.Charset.forName("UTF-8")
  implicit val byteOrder = ByteOrder.BIG_ENDIAN
  implicit val charEncoding = java.nio.charset.Charset.defaultCharset()
  var buffer = ByteString.empty

  def apply(ctx: PipelineContext) = new SymmetricPipePair[Pdu, ByteString]{

    /**
     * PDU -> ByteString
     */
    override def commandPipeline: (Pdu) => Iterable[this.type#Result] = { pdu =>
      ctx.singleCommand(pdu.toByteString)
    }

    /**
     * ByteString -> PDU
     */
    override def eventPipeline: (ByteString) => Iterable[this.type#Result] = { bs: ByteString =>

      val (newBuffer, pdus) = pduFromBuffer(buffer ++ bs, Nil)
      buffer = newBuffer
      pdus match {
        case Nil => Nil
        case one :: Nil => ctx.singleEvent(one)
        case n => n.reverseMap(Left(_))
      }
    }

    @tailrec
    def pduFromBuffer(bs: ByteString, acc: List[Pdu]): (ByteString, Seq[Pdu]) = {
      // let's get at least a header before proceeding...
      if (!(bs.length > 4 && bs.length >= bs.iterator.getInt)) {
        (bs, acc)
      } else {
        val commandLength = bs.iterator.getInt
        val (pdu, extra) = bs.splitAt(commandLength)
        pduFromBuffer(extra, Pdu.fromBytes(pdu.iterator) :: acc)
      }
    }
  }
}
