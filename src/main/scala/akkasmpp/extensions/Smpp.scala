package akkasmpp.extensions

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor._
import akka.io.Inet
import akka.io.Inet.SocketOption
import akka.stream.scaladsl._
import akka.stream.{BidiShape, Materializer}
import akka.util.ByteString
import akkasmpp.protocol.{Pdu, SmppPduFramingStage}
import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Akka extension for Smpp
  */
class SmppExt(config: Config)(implicit system: ActorSystem)
    extends akka.actor.Extension {

  type ServerLayer = BidiFlow[Pdu, ByteString, ByteString, Pdu, NotUsed]

  private val blueprint = BidiFlow.fromGraph(GraphDSL.create() { builder =>
    val output = builder.add(Flow[Pdu].map(_.toByteString))
    val input = builder.add(new SmppPduFramingStage(config))
    BidiShape.fromFlows(output, input)
  })

  import Smpp._
  def listen(interface: String,
             port: Int = 2775,
             backlog: Int = 100,
             options: immutable.Traversable[Inet.SocketOption] = Nil,
             idleTimeout: Duration = Duration.Inf)(implicit fm: Materializer)
    : Source[IncomingConnection, Future[ServerBinding]] = {
    val tcpConnections =
      Tcp().bind(interface, port, backlog, options, idleTimeout = idleTimeout)
    tcpConnections.map {
      case Tcp.IncomingConnection(localAddress, remoteAddress, flow) =>
        IncomingConnection(localAddress, remoteAddress, blueprint.join(flow))
    }
  }.mapMaterializedValue {
    _.map(tcpBinding =>
          ServerBinding(tcpBinding.localAddress)(() => tcpBinding.unbind()))(
        fm.executionContext)
  }

  def connect(remoteAddress: InetSocketAddress,
              localAddress: Option[InetSocketAddress] = None,
              options: immutable.Traversable[SocketOption] = Nil,
              halfClose: Boolean = true,
              connectTimeout: Duration = Duration.Inf,
              idleTimeout: Duration = Duration.Inf)(implicit fm: Materializer)
    : Flow[Pdu, Pdu, Future[OutgoingConnection]] = {

    val connection = Tcp().outgoingConnection(remoteAddress,
                                              localAddress,
                                              options,
                                              halfClose,
                                              connectTimeout,
                                              idleTimeout)
    blueprint
      .joinMat(connection)(Keep.right)
      .mapMaterializedValue(
          // XXX: maybe just use the Tcp.OutgoingConnection? is there actually a reason to wrap it?
          _.map(tcp =>
                OutgoingConnection(tcp.remoteAddress, tcp.localAddress))(
              fm.executionContext))
  }
}

object Smpp extends ExtensionId[SmppExt] with ExtensionIdProvider {

  case class IncomingConnection(localAddress: InetSocketAddress,
                                remoteAddress: InetSocketAddress,
                                flow: Flow[Pdu, Pdu, NotUsed]) {
    /* is this needed?
    def handle[Mat](f: Flow[Pdu, Pdu, Mat])(implicit mat: Materializer) = {
      flow.joinMat(f)(Keep.right).run()
    }
   */
  }

  case class OutgoingConnection(
      remoteAddress: InetSocketAddress, localAddress: InetSocketAddress)

  override def createExtension(system: ExtendedActorSystem): SmppExt =
    new SmppExt(system.settings.config getConfig "smpp")(system)

  override def lookup(): ExtensionId[_ <: Extension] = Smpp

  case class ServerBinding(localAddress: InetSocketAddress)(
      private val unbindAction: () ⇒ Future[Unit]) {

    /**
      * Asynchronously triggers the unbinding of the port that was bound by the materialization of the `connections`
      * [[Source]]
      *
      * The produced [[Future]] is fulfilled when the unbinding has been completed.
      */
    def unbind(): Future[Unit] = unbindAction()
  }
}
