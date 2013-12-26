package akkasmpp.actors

import akka.actor.{ActorRef, Deploy, Actor, ActorLogging}
import java.net.InetSocketAddress

import akkasmpp.protocol.{EnquireLink, NumericPlanIndicator, TypeOfNumber, BindTransmitter, Pdu, SmppFramePipeline}
import akka.io.{TcpReadWriteAdapter, TcpPipelineHandler, Tcp, IO}
import akka.io.TcpPipelineHandler.WithinActorContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Basic ESME behaviors
 */

class SmppClient(val serverAddress: InetSocketAddress) extends Actor with ActorLogging {

  import akka.io.Tcp._
  import scala.concurrent.duration._
  import context.system

  val manager = IO(Tcp)
  val sequenceNumberGen = new AtomicInteger

  log.debug(s"Connecting to server at $serverAddress")
  manager ! Connect(serverAddress, timeout = Some(3.seconds))

  override def postStop = {
    manager ! Close
  }

  def receive: Actor.Receive = {
    case CommandFailed(_: Connect) =>
      log.error("Network connection failed")
      context stop self
    case c @ Connected(remote, local) =>
      log.debug(s"Connection established to server at $remote")

      val pipeline = TcpPipelineHandler.withLogger(log, new SmppFramePipeline >> new TcpReadWriteAdapter)

      val handler = context.actorOf(TcpPipelineHandler.props(pipeline, sender, self).withDeploy(Deploy.local))
      context.watch(handler)

      sender ! Tcp.Register(handler)
      handler ! pipeline.Command(BindTransmitter(0x1, "smppclient1", "password", None, 0x34, TypeOfNumber.International, NumericPlanIndicator.E164))
      context.become(awaitBindResp(pipeline, handler))
  }

  def awaitBindResp(wire: TcpPipelineHandler.Init[WithinActorContext, Pdu, Pdu], connection: ActorRef): Actor.Receive = {
    case wire.Event(p: Pdu) =>
      log.info(s"Got a pdu $p")
      connection ! wire.Command(
        EnquireLink(sequenceNumberGen.incrementAndGet())
      )
    case x => log.info(s"unexpected event! $x")

  }
}
