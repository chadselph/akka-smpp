package akkasmpp.actors

import java.net.InetSocketAddress
import java.time.Instant

import akka.NotUsed
import akka.actor._
import akka.pattern.pipe
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akkasmpp.actors.SmppServer._
import akkasmpp.extensions.Smpp
import akkasmpp.extensions.Smpp.{IncomingConnection, ServerBinding}
import akkasmpp.protocol.SmppTypes.SequenceNumber
import akkasmpp.protocol._
import akkasmpp.protocol.auth.{BindAuthenticator, BindRequest, BindResponseError, BindResponseSuccess}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class SmppServerConfig(
    bindAddr: InetSocketAddress, enquireLinkTimeout: Duration = 60.seconds)

object SmppServer {

  case class SendRawPdu(p: (SequenceNumber) => SmscRequest)
  case class ReadyIncomingSmppConnection(
      connection: ActorRef, addr: InetSocketAddress)

  class PendingInboundSmppConnection(val incoming: IncomingConnection,
                                     runnableGraph: RunnableGraph[ActorRef],
                                     since: Instant)(
      implicit val mat: Materializer) {
    def open(): OpenInboundSmppConnection =
      OpenInboundSmppConnection(
          localAddress = incoming.localAddress,
          remoteAddress = incoming.remoteAddress,
          source = runnableGraph.run,
          flow = incoming.flow,
          since = Instant.now()
      )
  }

  case class OpenInboundSmppConnection(
      localAddress: InetSocketAddress,
      remoteAddress: InetSocketAddress,
      source: ActorRef,
      flow: Flow[Pdu, Pdu, NotUsed],
      since: Instant
  )

  case object Disconnected
  case class BindResult(result: Boolean)

  def props(config: SmppServerConfig,
            handlerSpec: => SmppServerHandler,
            pduLogger: PduLogger =
              PduLogger.default)(implicit mat: Materializer) =
    Props(new SmppServer(config, handlerSpec, pduLogger))
}

class SmppServer(config: SmppServerConfig,
                 handlerSpec: => SmppServerHandler,
                 pduLogger: PduLogger = PduLogger.default)(
    implicit val mat: Materializer)
    extends Actor
    with ActorLogging
    with Stash {

  // ingress flow
  val flow: Source[IncomingConnection, Future[ServerBinding]] =
    Smpp(context.system).listen(config.bindAddr.getHostString,
                                config.bindAddr.getPort,
                                idleTimeout = config.enquireLinkTimeout)

  val binding = flow
    .to(Sink.foreach { incomingConnection =>
      val pduSource =
        Source.actorRef[Pdu](8, OverflowStrategy.dropNew) // this should really be an ActorPublisher
      // so we can have back-pressure integrated
      val handler = context.actorOf(Props(handlerSpec))
      val pduSink = Sink.actorRef[Pdu](handler, Disconnected)

      val graph = pduSource
        .map(pduLogger.doLogOutgoing)
        .via(incomingConnection.flow.map(pduLogger.doLogIncoming))
        .to(pduSink)
      handler ! new PendingInboundSmppConnection(
          incomingConnection, graph, Instant.now())
    })
    .run()

  binding.onComplete {
    case Success(ServerBinding(localAddr)) =>
      log.info(s"Started new SMPP server listening on $localAddr")
    case Failure(ex) => throw ex
  }(context.dispatcher)

  override def receive: Actor.Receive = {
    case () =>
  }
}

abstract class SmppServerHandler
    extends SmppActor
    with ActorLogging
    with Stash {

  val sequenceNumberGen = new AtomicIntegerSequenceNumberGenerator
  var window = Map[SequenceNumber, ActorRef]()
  val serverSystemId = "akka"
  implicit val ec    = context.dispatcher

  // XXX: split out into bound transmit vs bound receive
  def bound(connection: ActorRef): Actor.Receive
  def bindAuthenticator: BindAuthenticator

  def receive: Receive = init

  def init: Receive = receiveDisconnect orElse {
    case conn: PendingInboundSmppConnection =>
      log.info(
          s"Pending inbound connection from ${conn.incoming.remoteAddress}")
      context.become(binding(conn.open()))
  }

  def binding(connection: OpenInboundSmppConnection): Actor.Receive =
    receiveDisconnect orElse {
      case bt: BindLike =>
        bindAuthenticator
          .allowBind(BindRequest.fromBindLike(bt),
                     connection.remoteAddress,
                     connection.localAddress)
          .pipeTo(self)
      case BindResponseSuccess(pdu) =>
        connection.source ! pdu
        context.become(bound(connection.source))
      case BindResponseError(pdu) =>
        connection.source ! pdu
    }

  def smscRequestReply(connection: ActorRef): Actor.Receive = {
    case SendRawPdu(p) => // <-- XXX: fix this name
      val pdu = p(sequenceNumberGen.next)
      log.info(s"Sending raw pdu $pdu to ")
      // XXX: send tcp?
      connection ! pdu
      window = window.updated(pdu.sequenceNumber, sender())
    case r: EsmeResponse if window.contains(r.sequenceNumber) =>
      log.debug(s"Forwarding along $r")
      window(r.sequenceNumber) ! r
      window = window - r.sequenceNumber
  }

  def receiveDisconnect: Actor.Receive = {
    case Disconnected =>
      log.info("Client disconnected.")
      context.stop(self)
  }
}
