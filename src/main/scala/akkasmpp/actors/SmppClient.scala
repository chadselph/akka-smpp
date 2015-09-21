package akkasmpp.actors

import java.net.InetSocketAddress
import javax.net.ssl.SSLContext

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props, ReceiveTimeout}
import akka.io.Tcp.{ConnectionClosed, PeerClosed}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akkasmpp.actors.SmppClient._
import akkasmpp.extensions.Smpp
import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.protocol.NumericPlanIndicator.NumericPlanIndicator
import akkasmpp.protocol.SmppTypes.SequenceNumber
import akkasmpp.protocol.TypeOfNumber.TypeOfNumber
import akkasmpp.protocol.{AtomicIntegerSequenceNumberGenerator, BindReceiver, BindRespLike, BindTransceiver, BindTransmitter, COctetString, CommandStatus, EnquireLink, EnquireLinkResp, EsmeRequest, EsmeResponse, GenericNack, NumericPlanIndicator, Pdu, PduLogger, SmscRequest, SmscResponse, TypeOfNumber, Unbind, UnbindResp}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

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
  def props(config: SmppClientConfig, receiver: ClientReceive, pduLogger: PduLogger = PduLogger.default)
           (implicit mat: Materializer) =
    Props(new SmppClient(config, receiver, pduLogger))
  def connect(config: SmppClientConfig, receive: ClientReceive, name: String, pduLogger: PduLogger = PduLogger.default)
             (implicit ac: ActorRefFactory, mat: Materializer) = {
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
   * Send a PDU over the SMPP connection
   * Since the connection determines the SequenceNumber, pass a function that takes a new sequence
   * number and returns the PDU you want.
   * Example:
   *    SendRawPdu(myPdu.copy(sequenceNumber = _))
   */
  case class SendPdu(newPdu: SequenceNumber => EsmeRequest) extends Command

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
                            sslContext: Option[SSLContext] = None, autoBind: Option[Bind] = None,
                            connectTimeout: Duration = 5.seconds)

/**
 * Example SmppClient using the PDU layer
 */
class SmppClient(config: SmppClientConfig, receiver: ClientReceive, pduLogger: PduLogger = PduLogger.default)
                (implicit mat: Materializer)
  extends SmppActor with ActorLogging {

  override val sequenceNumberGen = new AtomicIntegerSequenceNumberGenerator
  var window = Map[SequenceNumber, ActorRef]()

  val connectionFlow = Smpp(context.system).connect(config.bindTo, idleTimeout = config.enquireLinkTimer * 2,
    connectTimeout = config.connectTimeout)
  val pduSource = Source.actorRef[Pdu](8, OverflowStrategy.dropNew)
  val pduSink = Sink.actorRef[Pdu](self, PeerClosed)
  val (connection, connectionInfo) = pduSource.map(pduLogger.doLogOutgoing)
    .viaMat(connectionFlow.map(pduLogger.doLogIncoming))(Keep.both).to(pduSink).run()
  connectionInfo.onComplete {
    case Success(conn) =>
      self ! conn
    case Failure(error) =>
      self ! error
  }(context.dispatcher)

  context.setReceiveTimeout(config.enquireLinkTimer * 2)

  log.info(s"Connecting to server at " + config.bindTo.toString)
  context.watch(connection)

  def receive = connecting

  def connecting: Actor.Receive = {
    case conn @ Smpp.OutgoingConnection(remote, local) =>
      log.info(s"Connection established to server at $remote")
      context.parent ! conn
      config.autoBind.foreach { self.tell(_, context.parent) } // send bind command to yourself if it's configured for autobind
      context.become(bind)
    case ex: Throwable =>
      log.error(s"Connection could not be established. $ex")
      throw ex
  }

  def bind: Actor.Receive = {
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
      connection ! cmd
      // XXX: receive timeout?
      context.become(binding(sender()))
    case cc: ConnectionClosed => throw new PeerClosed()
    case ReceiveTimeout => disconnect(new PeerTimedOut(config.enquireLinkTimer * 2))
  }

  def binding(requester: ActorRef): Actor.Receive = {
    // Future improvement: Type tags to ensure the response is the same as the request?
    case p: BindRespLike =>
      requester ! p
      if (p.commandStatus == CommandStatus.ESME_ROK) {
        log.info(s"Bound: $p")
        // start timers
        config.enquireLinkTimer match {
          case f: FiniteDuration =>
            log.debug("Starting EnquireLink loop")
            context.system.scheduler.schedule(f/2, f, self, SendPdu(EnquireLink))(context.dispatcher)
            context.setReceiveTimeout(f * 2)
          case _ =>
        }
        context.become(bound)
      } else {
        disconnect(new BindFailed(p.commandStatus))
      }
    case cc: ConnectionClosed => throw new PeerClosed()
    case ReceiveTimeout => disconnect(new PeerTimedOut(config.enquireLinkTimer * 2))

  }

  import SmppClient.SendPdu
  def bound: Actor.Receive = {
    case SendPdu(newPdu) =>
      val pdu = newPdu(sequenceNumberGen.next)
      log.info(s"sending raw pdu $pdu")
      connection ! pdu
      window = window.updated(pdu.sequenceNumber, sender())

    case pdu: SmscResponse if window.get(pdu.sequenceNumber).isDefined =>
      log.debug(s"Incoming SubmitSmResp $pdu")
      window(pdu.sequenceNumber) ! pdu
      window = window - pdu.sequenceNumber
    case pdu: EnquireLinkResp =>
      log.debug(s"got enquire_link_resp")
    case pdu: SmscResponse =>
      log.warning(s"Response for unknown sequence number: $pdu")
    case EnquireLink(seq) =>
      connection ! EnquireLinkResp(seq)
    case msg: SmscRequest if receiver.isDefinedAt(msg) =>
      // XXX: also make this async for Futures
      val resp = receiver(msg)
      log.debug(s"Replying to $msg with $resp")
      connection ! resp

    case Unbind(seqN) =>
      // XXX: what will happen to "inflight" requests?
      log.info(s"Received Unbind, closing connection")
      connection ! UnbindResp(CommandStatus.ESME_ROK, seqN)
      throw new UnbindReceived()

    case pdu: Pdu =>
      log.warning(s"Received unsupported pdu: $pdu responding with GenericNack")
      connection ! GenericNack(CommandStatus.ESME_RCANCELFAIL, pdu.sequenceNumber)

    case cc: ConnectionClosed => throw new PeerClosed()

    case ReceiveTimeout => disconnect(new PeerTimedOut(config.enquireLinkTimer * 2))

  }

  /**
   * Disconnect from the connection and throw an exception to our supervisor.
   * @param reason The reason is important for the supervisor strategy to decide about restarting.
   */
  def disconnect(reason: Throwable) = {
    connection ! akka.actor.Status.Failure(reason)
    throw reason
  }

}
