package akkasmpp.actors

import akka.actor.{ActorRef, Deploy, Actor, ActorLogging}
import java.net.InetSocketAddress

import akkasmpp.protocol.{Priority, DataCodingScheme, RegisteredDelivery, NullTime, EsmClass, ServiceType, SubmitSm, EnquireLink, NumericPlanIndicator, TypeOfNumber, BindTransmitter, Pdu, SmppFramePipeline}
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

  override def postStop() = {
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
      handler ! pipeline.Command(BindTransmitter(0x1, "smppclient1".getBytes, "password".getBytes, "".getBytes, 0x34, TypeOfNumber.International, NumericPlanIndicator.E164))
      context.become(awaitBindResp(pipeline, handler))
  }

  def awaitBindResp(wire: TcpPipelineHandler.Init[WithinActorContext, Pdu, Pdu], connection: ActorRef): Actor.Receive = {
    case wire.Event(p: Pdu) =>
      log.info(s"Got a pdu $p")
      connection ! wire.Command(
        EnquireLink(sequenceNumberGen.incrementAndGet())
      )
      connection ! wire.Command(
        SubmitSm(sequenceNumberGen.incrementAndGet(), ServiceType.Default, TypeOfNumber.International, NumericPlanIndicator.E164,
                 "15094302095".getBytes, TypeOfNumber.International, NumericPlanIndicator.E164, "+15094302095".getBytes,
                 EsmClass(EsmClass.MessagingMode.Default, EsmClass.MessageType.NormalMessage), 0x34, Priority.Level0,
                 NullTime, NullTime, RegisteredDelivery(), false, DataCodingScheme.SmscDefaultAlphabet,
                 0x0, 10, "0123456789".getBytes, Nil)
      )
    case x => log.info(s"unexpected event! $x")

  }
}
