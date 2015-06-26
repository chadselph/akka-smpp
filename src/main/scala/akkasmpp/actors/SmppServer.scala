package akkasmpp.actors

import java.net.InetSocketAddress
import java.nio.charset.Charset

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.{IO, Tcp}
import akkasmpp.actors.SmppServer.SendRawPdu
import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.protocol.SmppTypes.SequenceNumber
import akkasmpp.protocol.{AtomicIntegerSequenceNumberGenerator, BindLike, BindReceiver, BindRespLike, BindTransceiver, BindTransceiverResp, BindTransmitter, COctetString, CommandStatus, EsmeResponse, OctetString, PduLogger, SmppTypes, SmscRequest, Tag, Tlv}

import scala.concurrent.duration._

case class SmppServerConfig(bindAddr: InetSocketAddress, enquireLinkTimeout: Duration = 60.seconds)

object SmppServer {

  case class SendRawPdu(p: (SequenceNumber) => SmscRequest)

}

class SmppServer(config: SmppServerConfig,  pduLogger: PduLogger = PduLogger.default) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  val manager = IO(Tcp)

  log.info(s"Starting new SMPP server listening on ${config.bindAddr}")
  manager ! Bind(self, config.bindAddr)

  override def receive = {
    case Bound(localAddress) =>
      log.info(s"SMPP server bound to $localAddress")
    case CommandFailed(b: Bind) =>
      log.error(s"Failed to bind $b")
      context stop self
    case Connected(remote, local) =>
      log.info(s"New connection from $remote")
      /*
      New SMPP connection, need to start a handler.
       */
      val connection = sender()
      log.debug(s"connection is $connection")
      sender ! Register(self)
  }

}

abstract class SmppServerHandler(val connection: ActorRef) extends SmppActor with ActorLogging {

  implicit val cs = Charset.forName("UTF-8")
  val sequenceNumberGen = new AtomicIntegerSequenceNumberGenerator
  var window = Map[SequenceNumber, ActorRef]()
  val serverSystemId = "akka"

  override def receive: Actor.Receive = binding

  type BindResponse = (CommandStatus, SmppTypes.Integer, Option[COctetString], Option[Tlv]) => BindRespLike
  // XXX: figure out how to incorporate bind auth
  private def doBind(bindRecv: BindLike, respFactory: BindResponse) = {
    log.info(s"got a bind like message $bindRecv")
    respFactory(CommandStatus.ESME_ROK,
      bindRecv.sequenceNumber, Some(COctetString.ascii(serverSystemId)),
      Some(Tlv(Tag.SC_interface_version, OctetString(0x34: Byte))))
  }

  def binding: Actor.Receive = {
    case bt: BindTransceiver =>
      sender ! doBind(bt, BindTransceiverResp.apply)
      context.become(bound)
    case br: BindReceiver =>
      sender ! doBind(br, BindTransceiverResp.apply)
      context.become(bound)
    case bt: BindTransmitter =>
      sender ! doBind(bt, BindTransceiverResp.apply)
      context.become(bound)

  }

  // XXX: split out into bound transmit vs bound receive
  def bound: Actor.Receive

  def smscRequestReply: Actor.Receive = {
    case SendRawPdu(p) =>
      val pdu = p(sequenceNumberGen.next)
      log.info(s"Sending raw pdu $pdu to $connection")
      // XXX: send tcp?
      window = window.updated(pdu.sequenceNumber, sender())
    case r: EsmeResponse if window.contains(r.sequenceNumber) =>
      log.debug(s"Forwarding along $r")
      window(r.sequenceNumber) ! r
      window = window - r.sequenceNumber
  }
}

