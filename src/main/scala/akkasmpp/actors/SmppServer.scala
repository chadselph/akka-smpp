package akkasmpp.actors

import java.net.InetSocketAddress
import akka.actor.{ActorSystem, Actor, Deploy, ActorRef, ActorLogging, Stash, Props}
import scala.concurrent.duration._
import akka.io.{TcpReadWriteAdapter, IO, Tcp, TcpPipelineHandler}
import akka.io.TcpPipelineHandler.WithinActorContext
import akkasmpp.protocol.{PduLogger, SmppTypes, BindRespLike, BindLike, BindTransmitterResp, EsmeResponse, SmscRequest, OctetString, Tag, Tlv, COctetString, CommandStatus, BindTransceiverResp, BindTransmitter, BindReceiver, BindTransceiver, AtomicIntegerSequenceNumberGenerator, Pdu, SmppFramePipeline}
import akkasmpp.protocol.SmppTypes.SequenceNumber
import akkasmpp.actors.SmppServerHandler.SmppPipeLine
import java.nio.charset.Charset
import scala.concurrent.ExecutionContext
import akkasmpp.actors.SmppServer.SendRawPdu
import akkasmpp.protocol.CommandStatus.CommandStatus

case class SmppServerConfig(bindAddr: InetSocketAddress, enquireLinkTimeout: Duration = 60.seconds)

object SmppServer {

  case class SendRawPdu(p: (SequenceNumber) => SmscRequest)

  def props(config: SmppServerConfig, handlerSpec: (SmppServerHandler.SmppPipeLine, ActorRef) => SmppServerHandler,
            pduLogger: PduLogger = PduLogger.default) =
    Props(classOf[SmppServer], config, handlerSpec, pduLogger)

  def run(host: String, port: Int, enquireLinkTimeout: Duration = 60.seconds)
         (actor: (SmppServerHandler.SmppPipeLine, ActorRef) => SmppServerHandler)
         (implicit as: ActorSystem) = {
    as.actorOf(SmppServer.props(
      SmppServerConfig(new InetSocketAddress(host, port), enquireLinkTimeout), actor))

  }
}

class SmppServer(config: SmppServerConfig, handlerSpec: (SmppServerHandler.SmppPipeLine, ActorRef) => SmppServerHandler,
                 pduLogger: PduLogger = PduLogger.default)
  extends Actor with ActorLogging with Stash {

  import context.system
  import Tcp._

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
      val connection = sender
      log.debug(s"connection is $connection")
      val init = TcpPipelineHandler.withLogger(log, new SmppFramePipeline(pduLogger) >> new TcpReadWriteAdapter)
      val handler = context.actorOf(Props(handlerSpec(init, connection)))
      val pipeline = context.actorOf(TcpPipelineHandler.props(init, connection, handler).withDeploy(Deploy.local))
      sender ! Register(pipeline)
  }

}

object SmppServerHandler {
  type SmppPipeLine = TcpPipelineHandler.Init[WithinActorContext, Pdu, Pdu]

  def props(wire: SmppPipeLine) = Props(classOf[SmppServerHandler], wire)
}

abstract class SmppServerHandler(val wire: SmppPipeLine, val connection: ActorRef)(implicit val ec: ExecutionContext)
  extends SmppActor with ActorLogging {

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
      bindRecv.sequenceNumber, Some(new COctetString(serverSystemId)),
      Some(Tlv(Tag.SC_interface_version, new OctetString(0x34: Byte))))
  }

  def binding: Actor.Receive = {
    case wire.Event(bt: BindTransceiver) =>
      sender ! wire.Command(doBind(bt, BindTransceiverResp.apply))
      context.become(bound)
    case wire.Event(br: BindReceiver) =>
      sender ! wire.Command(doBind(br, BindTransceiverResp.apply))
      context.become(bound)
    case wire.Event(bt: BindTransmitter) =>
      sender ! wire.Command(doBind(bt, BindTransceiverResp.apply))
      context.become(bound)

  }

  // XXX: split out into bound transmit vs bound receive
  def bound: Actor.Receive

  def smscRequestReply: Actor.Receive = {
    case SendRawPdu(p) =>
      val pdu = p(sequenceNumberGen.next)
      log.info(s"Sending raw pdu $pdu to $connection")
      // seems to be a design flaw in Pipelines...
      connection ! Tcp.Write(pdu.toByteString)
      window = window.updated(pdu.sequenceNumber, sender)
    case wire.Event(r: EsmeResponse) if window.contains(r.sequenceNumber) =>
      log.debug(s"Forwarding along $r")
      window(r.sequenceNumber) ! r
      window = window - r.sequenceNumber
  }
}

