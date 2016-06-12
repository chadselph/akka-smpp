package akkasmpp.extensions

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.io.Inet
import akka.io.Inet.SocketOption
import akka.stream.TLSProtocol.{SendBytes, SessionBytes, SslTlsInbound, SslTlsOutbound}
import akka.stream.scaladsl.{BidiFlow, Flow, Keep, Source, TLS, TLSPlacebo, Tcp}
import akka.stream.{Materializer, TLSRole}
import akka.util.ByteString
import akkasmpp.protocol.{Pdu, SmppPduFramingStage}
import akkasmpp.ssl.TlsContext
import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Akka extension for Smpp
  */
class SmppExt(config: Config)(implicit system: ActorSystem)
    extends akka.actor.Extension {

  type SmppShape = BidiFlow[Pdu, SslTlsOutbound, SslTlsInbound, Pdu, NotUsed]

  val protocolLayer = BidiFlow.fromFlows(
      Flow[Pdu].map(_.toByteString), new SmppPduFramingStage(config))
  val tlsSupport: BidiFlow[
      ByteString, SslTlsOutbound, SslTlsInbound, ByteString, NotUsed] =
    BidiFlow.fromFlows(
        Flow[ByteString].map(SendBytes), Flow[SslTlsInbound].collect {
      case x: SessionBytes ⇒ x.bytes
    })
  val blueprint = protocolLayer.atop(tlsSupport)

  import Smpp._
  def listen(interface: String,
             port: Int = 2775,
             backlog: Int = 100,
             options: immutable.Traversable[Inet.SocketOption] = Nil,
             tlsContext: Option[TlsContext] = None,
             idleTimeout: Duration = Duration.Inf)(implicit fm: Materializer)
    : Source[IncomingConnection, Future[ServerBinding]] = {
    val tcpConnections =
      Tcp().bind(interface, port, backlog, options, idleTimeout = idleTimeout)
    tcpConnections.map {
      case Tcp.IncomingConnection(localAddress, remoteAddress, flow) =>
        val sslStage = sslTlsStage(tlsContext, TLSRole.server)
        IncomingConnection(
            localAddress, remoteAddress, blueprint.atop(sslStage).join(flow))
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
              tlsContext: Option[TlsContext] = None,
              idleTimeout: Duration = Duration.Inf)(implicit fm: Materializer)
    : Flow[Pdu, Pdu, Future[OutgoingConnection]] = {

    val connection = Tcp().outgoingConnection(remoteAddress,
                                              localAddress,
                                              options,
                                              halfClose,
                                              connectTimeout,
                                              idleTimeout)
    val sslStage = sslTlsStage(
        tlsContext,
        TLSRole.client,
        Some((remoteAddress.getHostString, remoteAddress.getPort)))
    blueprint
      .atop(sslStage)
      .joinMat(connection)(Keep.right)
      .mapMaterializedValue(
          // XXX: maybe just use the Tcp.OutgoingConnection? is there actually a reason to wrap it?
          _.map(tcp =>
                OutgoingConnection(tcp.remoteAddress, tcp.localAddress))(
              fm.executionContext))
  }

  /** Creates real or placebo SslTls stage based on if ConnectionContext is HTTPS or not. */
  private def sslTlsStage(connectionContext: Option[TlsContext],
                          role: TLSRole,
                          hostInfo: Option[(String, Int)] = None) =
    connectionContext match {
      case Some(tsc: TlsContext) =>
        TLS(tsc.sslContext, tsc.sslConfig, tsc.firstSession, role, hostInfo = hostInfo)
      case None ⇒ TLSPlacebo()
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
