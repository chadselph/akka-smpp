package akkasmpp.actors

import java.net.InetSocketAddress

import akka.actor._
import akka.pattern.pipe
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akkasmpp.actors.SmppServer.{Disconnected, NewConnection, SendRawPdu}
import akkasmpp.extensions.Smpp
import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.protocol.SmppTypes.SequenceNumber
import akkasmpp.protocol._
import akkasmpp.protocol.auth.{BindAuthenticator, BindRequest, BindResponseError, BindResponseSuccess}

import scala.concurrent.duration._

case class SmppServerConfig(bindAddr: InetSocketAddress, enquireLinkTimeout: Duration = 60.seconds)

object SmppServer {

  case class SendRawPdu(p: (SequenceNumber) => SmscRequest)
  case class NewConnection(connection: ActorRef)
  case object Disconnected
  case class BindResult(result: Boolean)

  def props(config: SmppServerConfig, handlerSpec: => SmppServerHandler, pduLogger: PduLogger = PduLogger.default)
           (implicit mat: Materializer) =
    Props(new SmppServer(config, handlerSpec, pduLogger))

}

class SmppServer(config: SmppServerConfig, handlerSpec: => SmppServerHandler, pduLogger: PduLogger = PduLogger.default)
                (implicit val mat: Materializer)
  extends Actor with ActorLogging with Stash {

  var target: ActorRef = null
  log.info(s"Starting new SMPP server listening on ${config.bindAddr}")
  val flow = Smpp(context.system).listen(config.bindAddr.getHostString, config.bindAddr.getPort,
    idleTimeout = config.enquireLinkTimeout)

  flow.runForeach(incomingConnection => {
    val pduSource = Source.actorRef[Pdu](8, OverflowStrategy.fail)
    val handler = context.actorOf(Props(handlerSpec))
    val pduSink = Sink.actorRef[Pdu](handler, Disconnected)
    target = pduSource.map(pduLogger.doLogOutgoing).via(incomingConnection.flow.map(pduLogger.doLogIncoming))
      .to(pduSink).run()
    handler ! NewConnection(target)
  })

  def receive = {
    case x: BindTransceiver =>
      println(x)
      target ! BindTransceiverResp(CommandStatus.ESME_ROK, x.sequenceNumber, None, None)
    case x => println(x)
  }
}

abstract class SmppServerHandler extends SmppActor with ActorLogging {

  val sequenceNumberGen = new AtomicIntegerSequenceNumberGenerator
  var window = Map[SequenceNumber, ActorRef]()
  val serverSystemId = "akka"
  implicit val ec = context.dispatcher

  override def receive: Actor.Receive = {
    case NewConnection(conn) => context.become(binding(conn))
  }

  type BindResponse = (CommandStatus, SmppTypes.Integer, Option[COctetString], Option[Tlv]) => BindRespLike
  // XXX: figure out how to incorporate bind auth
  private def doBind(bindRecv: BindLike, respFactory: BindResponse) = {
    log.info(s"got a bind like message $bindRecv")
    respFactory(CommandStatus.ESME_ROK,
      bindRecv.sequenceNumber, Some(COctetString.ascii(serverSystemId)),
      Some(Tlv(Tag.SC_interface_version, OctetString(0x34: Byte))))
  }

  def binding(connection: ActorRef): Actor.Receive = {
    case bt: BindLike =>
      bindAuthenticator.allowBind(BindRequest.fromBindLike(bt)).pipeTo(self)
    case BindResponseSuccess(pdu) =>
      connection ! pdu
      context.become(bound(connection))
    case BindResponseError(pdu) =>
      connection ! pdu
  }

  // XXX: split out into bound transmit vs bound receive
  def bound(connection: ActorRef): Actor.Receive
  def bindAuthenticator: BindAuthenticator

  def smscRequestReply: Actor.Receive = {
    case SendRawPdu(p) =>
      val pdu = p(sequenceNumberGen.next)
      log.info(s"Sending raw pdu $pdu to ")
      // XXX: send tcp?
      window = window.updated(pdu.sequenceNumber, sender())
    case r: EsmeResponse if window.contains(r.sequenceNumber) =>
      log.debug(s"Forwarding along $r")
      window(r.sequenceNumber) ! r
      window = window - r.sequenceNumber
  }
}

