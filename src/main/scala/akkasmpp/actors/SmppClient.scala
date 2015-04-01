package akkasmpp.actors

import akka.actor.{ReceiveTimeout, OneForOneStrategy, ActorRefFactory, Props, Stash, ActorRef, Deploy, Actor, ActorLogging}
import java.net.InetSocketAddress
import akkasmpp.protocol.{PduLogger, UnbindResp, Unbind, EsmeResponse, SmscRequest, SmscResponse, EsmeRequest, OctetString, COctetString, GenericNack, CommandStatus, EnquireLinkResp, BindRespLike, BindReceiver, BindTransceiver, AtomicIntegerSequenceNumberGenerator, Priority, DataCodingScheme, RegisteredDelivery, NullTime, EsmClass, ServiceType, SubmitSm, EnquireLink, NumericPlanIndicator, TypeOfNumber, BindTransmitter, Pdu, SmppFramePipeline}
import akka.io.{SslTlsSupport, TcpReadWriteAdapter, TcpPipelineHandler, Tcp, IO}
import akka.io.TcpPipelineHandler.WithinActorContext
import akkasmpp.protocol.NumericPlanIndicator.NumericPlanIndicator
import akkasmpp.protocol.TypeOfNumber.TypeOfNumber
import akkasmpp.protocol.DataCodingScheme.DataCodingScheme
import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.protocol.SmppTypes.SequenceNumber
import akkasmpp.actors.SmppClient.{PeerTimedOut, BindFailed, UnbindReceived, PeerClosed, ConnectionFailed, ClientReceive, Bind}
import scala.concurrent.duration.Duration
import javax.net.ssl.{SSLException, SSLContext}
import akkasmpp.ssl.SslUtil
import akka.actor.SupervisorStrategy.Escalate

/**
 * Basic ESME behaviors
 */

object SmppClient {

  abstract class SmppClientException(msg: String) extends Exception(msg)
  class PeerClosed extends SmppClientException("Peer dropped the connection unexpectedly.")
  class PeerTimedOut(time: Duration) extends SmppClientException(s"Peer went too long without responding to messages ($time), resetting connection.")
  class UnbindReceived extends SmppClientException("Peer sent Unbind request")
  class ConnectionFailed extends SmppClientException("Could not make TCP connection to the server.")
  class BindFailed(val errorCode: CommandStatus) extends SmppClientException("Bind failed with " + errorCode)

  type ClientReceive = PartialFunction[SmscRequest, EsmeResponse]
  def props(config: SmppClientConfig, receiver: ClientReceive, pduLogger: PduLogger = PduLogger.default) =
    Props(classOf[SmppClient], config, receiver, pduLogger)
  def connect(config: SmppClientConfig, receive: ClientReceive, name: String, pduLogger: PduLogger = PduLogger.default)(implicit ac: ActorRefFactory) = {
    ac.actorOf(SmppClient.props(config, receive, pduLogger), name)
  }

  object Implicits {
    implicit def stringAsDid(s: String): Did = Did(s)
  }

  abstract class BindMode
  object Transceiver extends BindMode
  object Transmitter extends BindMode
  object Receiver extends BindMode

  case class Did(number: String, `type`: TypeOfNumber = TypeOfNumber.International,
                 npi: NumericPlanIndicator = NumericPlanIndicator.E164)
  abstract class Command
  case class Bind(systemId: String, password: String, systemType: String = "",
                  mode: BindMode = Transceiver, addrTon: TypeOfNumber = TypeOfNumber.International,
                  addrNpi: NumericPlanIndicator = NumericPlanIndicator.E164) extends Command

  /**
   * Command to send a message through the SmppClient.
   * @param content Message to send. Will be translated into a Seq[SubmitSm] messages
   *                with the right ESM class and UDH headers for concat.
   * @param encoding None for default encoding (figures it out) or manually set an encoding.
   */
  case class SendMessage(content: String, to: Did, from: Did, encoding: Option[DataCodingScheme] = None) extends Command

  /**
   * Incoming message over the SMPP connection
   * @param content Decoded content of the message (assumes not binary)
   * @param to Who the message is intended for
   * @param from Who the message came from
   */
  case class ReceiveMessage(content: String, to: Did, from: Did) extends Command

  /**
   * Send a PDU over the SMPP connection
   * Since the connection determines the SequenceNumber, pass a function that takes a new sequence
   * number and returns the PDU you want.
   * Example:
   *    SendRawPdu(myPdu.copy(sequenceNumber = _))
   */
  case class SendRawPdu(newPdu: SequenceNumber => EsmeRequest) extends Command

  /**
   * Used internally
   */
  case object SendEnquireLink extends Command

  abstract class Response

  /**
   * Response for SendMessage, sent when submit_sm_resp is received
   * @param results Tuple of (Status, MessageId)
   *              CommandStatus is 0 for success, otherwise a failure
   *              MessageId is Some(messageId) if successful, otherwise, None
   *              MessageId is used later for delivery receipts
   */
  case class SendMessageAck(results: Seq[(CommandStatus, Option[String])]) extends Response
}

case class SmppClientConfig(bindTo: InetSocketAddress, enquireLinkTimer: Duration = Duration.Inf,
                            sslContext: Option[SSLContext] = None, autoBind: Option[Bind] = None)

/**
 * Example SmppClient using the PDU layer
 */
class SmppClient(config: SmppClientConfig, receiver: ClientReceive, pduLogger: PduLogger = PduLogger.default)
  extends SmppActor with ActorLogging with Stash {

  type SmppPipeLine = TcpPipelineHandler.Init[WithinActorContext, Pdu, Pdu]

  import akka.io.Tcp.{Connect, Connected, CommandFailed, Close, ConnectionClosed}
  import scala.concurrent.duration._
  import context.system

  val manager = IO(Tcp)

  override val sequenceNumberGen = new AtomicIntegerSequenceNumberGenerator
  var window = Map[SequenceNumber, ActorRef]()

  log.debug(s"Connecting to server at " + config.bindTo.toString)
  manager ! Connect(config.bindTo, timeout = Some(3.seconds))

  override val supervisorStrategy = OneForOneStrategy() {
    case _: SSLException => Escalate
  }

  def receive = connecting

  def connecting: Actor.Receive = {
    case CommandFailed(_: Connect) =>
      throw new ConnectionFailed()
    case c @ Connected(remote, local) =>
      log.debug(s"Connection established to server at $remote")

      /*
      Decide if to do a TLS handshake or not
       */
      val stages = config.sslContext match {
        case None => new SmppFramePipeline(pduLogger) >> new TcpReadWriteAdapter
        case Some(sslContext) =>
          new SmppFramePipeline(pduLogger) >> new TcpReadWriteAdapter >>
            new SslTlsSupport(SslUtil.sslEngine(sslContext, remote, client = true))
      }

      val pipeline = TcpPipelineHandler.withLogger(log, stages)
      val handler = context.actorOf(TcpPipelineHandler.props(pipeline, sender, self).withDeploy(Deploy.local), "handler")
      context.watch(sender)
      sender ! Tcp.Register(handler)
      unstashAll()
      config.autoBind.foreach { self.tell(_, context.parent) } // send bind command to yourself if it's configured for autobind
      context.become(bind(pipeline, handler))
    case _ => stash()
  }

  def bind(wire: SmppPipeLine, connection: ActorRef): Actor.Receive = {
    case SmppClient.Bind(systemId, password, systemType, mode, addrTon, addrNpi) =>
      val bindFactory = mode match {
        case SmppClient.Transceiver => BindTransceiver(_, _, _, _, _, _, _, _)
        case SmppClient.Receiver => BindReceiver(_, _, _, _, _, _, _, _)
        case SmppClient.Transmitter => BindTransmitter(_, _, _, _, _, _, _, _)
      }
      implicit val encoding = java.nio.charset.Charset.forName("UTF-8")
      val cmd = bindFactory(sequenceNumberGen.next, COctetString.ascii(systemId), COctetString.ascii(password),
        COctetString.ascii(systemType), 0x34, addrTon, addrNpi, COctetString.empty)
      log.info(s"Making bind request $cmd")
      connection ! wire.Command(cmd)
      unstashAll()
      // XXX: receive timeout?
      context.become(binding(wire, connection, sender))
    case cc: ConnectionClosed => throw new PeerClosed()
    case _ => stash()
  }

  def binding(wire: SmppPipeLine, connection: ActorRef, requester: ActorRef): Actor.Receive = {
    // Future improvement: Type tags to ensure the response is the same as the request?
    case wire.Event(p: BindRespLike) =>
      requester ! p
      if (p.commandStatus == CommandStatus.ESME_ROK) {
        unstashAll()
        log.info(s"Bound: $p")
        // start timers
        config.enquireLinkTimer match {
          case f: FiniteDuration =>
            log.debug("Starting EnquireLink loop")
            context.system.scheduler.schedule(f/2, f, self, SmppClient.SendEnquireLink)(context.dispatcher)
            context.setReceiveTimeout(f * 2)
          case _ =>
        }
        context.become(bound(wire, connection))
      } else {
        throw new BindFailed(p.commandStatus)
      }
    case cc: ConnectionClosed => throw new PeerClosed()
    case c: SmppClient.Command => stash()

  }

  import SmppClient.{SendMessage, SendEnquireLink, SendRawPdu}
  def bound(wire: SmppPipeLine, connection: ActorRef): Actor.Receive = {
    case SendMessage(msg, to, from, encoding) =>
      // XXX: Support concat and non-ascii
      val body = msg.getBytes("ASCII")
      val seqNum = sequenceNumberGen.next
      implicit val encoding = java.nio.charset.Charset.forName("UTF-8")
      val cmd = SubmitSm(seqNum, ServiceType.Default, from.`type`, from.npi, COctetString.ascii(from.number),
                         to.`type`, to.npi, COctetString.ascii(to.number), EsmClass(EsmClass.MessagingMode.Default, EsmClass.MessageType.NormalMessage),
                         0x34, Priority.Level0, NullTime, NullTime, RegisteredDelivery(), false, DataCodingScheme.SmscDefaultAlphabet,
                         0x0, body.length.toByte, OctetString.fromBytes(body), Nil)
      log.info(s"Sending message $cmd")
      connection ! wire.Command(cmd)
      window = window.updated(seqNum, context.actorOf(SubmitSmRespWatcher.props(Set(seqNum), sender),
        s"seqN-${cmd.sequenceNumber}"))

    case SendEnquireLink =>
      log.debug("sending enquire link!")
      connection ! wire.Command(EnquireLink(sequenceNumberGen.next))

    case SendRawPdu(newPdu) =>
      val pdu = newPdu(sequenceNumberGen.next)
      log.debug(s"sending raw pdu $pdu")
      connection ! wire.Command(pdu)
      window = window.updated(pdu.sequenceNumber, sender)

    case wire.Event(pdu: SmscResponse) if window.get(pdu.sequenceNumber).isDefined =>
      log.debug(s"Incoming SubmitSmResp $pdu")
      window(pdu.sequenceNumber) ! pdu
      window = window - pdu.sequenceNumber
    case wire.Event(pdu: EnquireLinkResp) =>
      log.debug(s"got enquire_link_resp")
      // XXX: update some internal timer?
    case wire.Event(pdu: SmscResponse) =>
      log.warning(s"Response for unknown sequence number: $pdu")
    case wire.Event(EnquireLink(seq)) =>
      connection ! wire.Command(EnquireLinkResp(seq))
    case wire.Event(msg: SmscRequest) if receiver.isDefinedAt(msg) =>
      // XXX: also make this async for Futures
      val resp = receiver(msg)
      log.debug(s"Replying to $msg with $resp")
      connection ! wire.Command(resp)

    case wire.Event(Unbind(seqN)) =>
      // XXX: what will happen to "inflight" requests?
      log.info(s"Received Unbind, closing connection")
      connection ! wire.Command(UnbindResp(CommandStatus.ESME_ROK, seqN))
      manager ! Close
      throw new UnbindReceived()

    case wire.Event(pdu: Pdu) =>
      log.warning(s"Received unsupported pdu: $pdu responding with GenericNack")
      connection ! wire.Command(GenericNack(CommandStatus.ESME_RCANCELFAIL, pdu.sequenceNumber))

    case cc: ConnectionClosed => throw new PeerClosed()

    case ReceiveTimeout => throw new PeerTimedOut(config.enquireLinkTimer * 2)

  }
}
