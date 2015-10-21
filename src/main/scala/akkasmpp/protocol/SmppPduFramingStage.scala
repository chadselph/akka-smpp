package akkasmpp.protocol

import akka.stream.scaladsl.Framing.FramingException
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.typesafe.config.Config

/**
  * Stateful parser for reading PDUs from TCP data
  *
  * Loosely based on the generic LengthFieldFramingStage, but SMPP
  * counts the length parameter as part of the length of the PDU,
  * [[akka.stream.scaladsl.Framing.lengthField]] does not have this
  * option yet.
  */
class SmppPduFramingStage(config: Config)
    extends GraphStage[FlowShape[ByteString, Pdu]] {

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN
  val in = Inlet[ByteString]("SmppPduFramingStage.in")
  val out = Outlet[Pdu]("SmppPduFramingStage.out")

  override val shape: FlowShape[ByteString, Pdu] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =

    new GraphStageLogic(shape) with InHandler with OutHandler {

      var buffer = ByteString.empty

      override def onPush(): Unit = {
        buffer ++= grab(in)
        maybePush()
        if (buffer.isEmpty && isClosed(in)) {
          completeStage()
        }
      }

      override def onPull(): Unit = maybePush()

      override def onUpstreamFinish(): Unit = {
        if (buffer.isEmpty) {
          completeStage()
        } else if (isAvailable(out)) {
          maybePush()
        } // else swallow the termination and wait for pull
      }

      def maybePush(): Unit = {
        val (b, pduOption) = pduFromBuffer(buffer)
        buffer = b
        pduOption.foreach { pdu =>
          push(out, pdu)
        }
        if (pduOption.isEmpty) tryPull()
      }

      private def tryPull() = {
        if (isClosed(in)) {
          failStage(new FramingException("Stream finished but there was a truncated final frame in the buffer"))
        } else pull(in)
      }

      setHandlers(in, out, this)
    }

  final def pduFromBuffer(bs: ByteString): (ByteString, Option[Pdu]) = {
    if (bs.length < 4) {
      // we don't even have enough to read the length
      (bs, None)
    } else {
      val commandLength = bs.iterator.getInt
      if (commandLength > config.getInt("protocol.max-pdu-length") ||
        commandLength < config.getInt("protocol.min-pdu-length")) {
        throw new SmppProtocolError(s"Invalid command length: ${commandLength}")
      } else if (bs.length < commandLength) {
        (bs, None)
      } else {
        val (pdu, extra) = bs.splitAt(commandLength)
        val parsed = Pdu.fromBytes(pdu.iterator)
        (extra, Some(parsed))
      }
    }
  }
}
