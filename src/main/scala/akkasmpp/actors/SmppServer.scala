package akkasmpp.actors

import java.net.InetSocketAddress
import java.nio.charset.Charset

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import akkasmpp.actors.SmppServer.SendRawPdu
import akkasmpp.extensions.Smpp
import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.protocol.SmppTypes.SequenceNumber
import akkasmpp.protocol._

import scala.concurrent.duration._

case class SmppServerConfig(bindAddr: InetSocketAddress, enquireLinkTimeout: Duration = 60.seconds)

object SmppServer {

  case class SendRawPdu(p: (SequenceNumber) => SmscRequest)

}

class SmppServer(config: SmppServerConfig,  pduLogger: PduLogger = PduLogger.default)
                (implicit mat: Materializer) extends Actor with ActorLogging {

  log.info(s"Starting new SMPP server listening on ${config.bindAddr}")
  val flow = Smpp(context.system).listen(config.bindAddr.getHostString, config.bindAddr.getPort,
    idleTimeout = config.enquireLinkTimeout)


  flow.runForeach(incomingConnection => {
    val pduSource = Source.actorRef[Pdu](8, OverflowStrategy.fail)
    val pduSink = Sink.actorRef[Pdu](self, Unit)
    val target = pduSource.via(incomingConnection.flow).to(pduSink).run()
  })

  def receive = {
    case x: Any =>
      println(x)
      sender() ! EnquireLink(12)
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

