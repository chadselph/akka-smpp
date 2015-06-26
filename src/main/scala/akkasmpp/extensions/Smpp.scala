package akkasmpp.extensions

import java.net.InetSocketAddress

import akka.actor._
import akka.io.Inet
import akka.stream.scaladsl._
import akka.stream.{BidiShape, Materializer}
import akka.util.ByteString
import akkasmpp.protocol.{Pdu, SmppPduParsingStage}
import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
 * Akka extension for Smpp
 */

class SmppExt(config: Config)(implicit system: ActorSystem) extends akka.actor.Extension {

  type ServerLayer = BidiFlow[Pdu, ByteString, ByteString, Pdu, Unit]

  import Smpp._
  def listen(interface: String, port: Int = 2775, backlog: Int = 100,
           options: immutable.Traversable[Inet.SocketOption] = Nil,
           idleTimeout: Duration = Duration.Inf)
          (implicit fm: Materializer): Source[IncomingConnection, Future[ServerBinding]] = {
    val tcpConnections = Tcp().bind(interface, port, backlog, options, idleTimeout = idleTimeout)
    tcpConnections.map {
      case Tcp.IncomingConnection(localAddress, remoteAddress, flow) =>
        val blueprint = BidiFlow() { builder =>
          val output = builder.add(Flow[Pdu].map(_.toByteString))
          val input = builder.add(Flow[ByteString]
            .transform(() => new SmppPduParsingStage(config)))

          //val mergedOutput = builder.add(Merge[Pdu](2))

          BidiShape(output, input)
        }
        IncomingConnection(localAddress, remoteAddress, blueprint.join(flow))
    }
  }.mapMaterializedValue {
    _.map(tcpBinding => ServerBinding(tcpBinding.localAddress)(() => tcpBinding.unbind()))(fm.executionContext)
  }


  def connect(host: String, port: Int,
              options: immutable.Traversable[Inet.SocketOption], idleTimeout: Duration = Duration.Inf)
             (implicit fm: Materializer): Source[Pdu, Unit] = {

    // XXX (easy): figure out the right parameters to use for connect()
    val connection = Tcp().outgoingConnection(host, port)
    ???
  }
}

object Smpp extends ExtensionId[SmppExt] with ExtensionIdProvider {

  case class IncomingConnection(
    localAddress: InetSocketAddress,
    remoteAddress: InetSocketAddress,
    flow: Flow[Pdu, Pdu, Unit]) {
    def handle[Mat](f: Flow[Pdu, Pdu, Mat])(implicit mat: Materializer) = {
      flow.joinMat(f)(Keep.right).run()
    }
  }


  override def createExtension(system: ExtendedActorSystem): SmppExt =
    new SmppExt(system.settings.config getConfig "smpp")(system)

  override def lookup(): ExtensionId[_ <: Extension] = Smpp

  case class ServerBinding(localAddress: InetSocketAddress)(private val unbindAction: () ⇒ Future[Unit]) {
    /**
     * Asynchronously triggers the unbinding of the port that was bound by the materialization of the `connections`
     * [[Source]]
     *
     * The produced [[Future]] is fulfilled when the unbinding has been completed.
     */
    def unbind(): Future[Unit] = unbindAction()
  }
}
