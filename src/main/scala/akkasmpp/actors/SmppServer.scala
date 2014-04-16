package akkasmpp.actors

import java.net.InetSocketAddress
import akka.actor.{ActorSystem, Actor, Deploy, ActorRef, ActorLogging, Stash, Props}
import scala.concurrent.duration._
import akka.io.{TcpReadWriteAdapter, IO, Tcp, TcpPipelineHandler}
import akka.io.TcpPipelineHandler.WithinActorContext
import akkasmpp.protocol.{SubmitSmResp, EnquireLinkResp, EnquireLink, SubmitSm, COctetString, CommandStatus, BindTransceiverResp, BindTransmitter, BindReceiver, BindTransceiver, AtomicIntegerSequenceNumberGenerator, Pdu, SmppFramePipeline}
import akkasmpp.protocol.SmppTypes.SequenceNumber
import akkasmpp.actors.SmppServerHandler.SmppPipeLine
import java.nio.charset.Charset
import scala.concurrent.ExecutionContext

case class SmppServerConfig(bindAddr: InetSocketAddress, enquireLinkTimeout: Duration = 60.seconds)

object SmppServer {
  def props(config: SmppServerConfig, handlerSpec: SmppServerHandler.SmppPipeLine => SmppServerHandler) =
    Props(classOf[SmppServer], config, handlerSpec)

  def run(host: String, port: Int, enquireLinkTimeout: Duration = 60.seconds)
         (actor: SmppServerHandler.SmppPipeLine => SmppServerHandler)
         (implicit as: ActorSystem) = {
    as.actorOf(SmppServer.props(
      SmppServerConfig(new InetSocketAddress(host, port), enquireLinkTimeout), actor))

  }
}

class SmppServer(config: SmppServerConfig, handlerSpec: SmppServerHandler.SmppPipeLine => SmppServerHandler)
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
      val init = TcpPipelineHandler.withLogger(log, new SmppFramePipeline >> new TcpReadWriteAdapter)
      val handler = context.actorOf(Props(handlerSpec(init)))
      val pipeline = context.actorOf(TcpPipelineHandler.props(init, sender, handler).withDeploy(Deploy.local))
      sender ! Register(pipeline)
  }

}

object SmppServerHandler {
  type SmppPipeLine = TcpPipelineHandler.Init[WithinActorContext, Pdu, Pdu]

  def props(wire: SmppPipeLine) = Props(classOf[SmppServerHandler], wire)
}

abstract class SmppServerHandler(val wire: SmppPipeLine)(implicit val ec: ExecutionContext)
  extends Actor with ActorLogging {

  implicit val cs = Charset.forName("UTF-8")
  val sequenceNumberGen = new AtomicIntegerSequenceNumberGenerator
  var window = Map[SequenceNumber, ActorRef]()

  override def receive: Actor.Receive = binding

  def binding: Actor.Receive = {
    case wire.Event(bt: BindTransceiver) =>
      log.info(s"got a bind like message $bt")
      sender ! wire.Command(BindTransceiverResp(CommandStatus.ESME_ROK,
        bt.sequenceNumber, COctetString.empty, None))
      context.become(bound)
    case wire.Event(br: BindReceiver) =>
    case wire.Event(bt: BindTransmitter) =>
  }

  def bound: Actor.Receive
}

