package akkasmpp

import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akkasmpp.extensions.Smpp
import akkasmpp.extensions.Smpp.ServerBinding
import akkasmpp.protocol.{BindTransceiver, BindTransceiverResp, COctetString, CommandStatus, Pdu, PduBuilder}
import akkasmpp.ssl.TlsContext
import akkasmpp.testutil.ByteStringHelpers
import org.scalatest.concurrent.{AsyncAssertions, Waiters}
import org.scalatest.{FlatSpec, Matchers}
import scala.concurrent.duration._

/**
  * Spin up a client and server and try to send a message.
  */
class EndToEndBindTest
    extends FlatSpec
    with Matchers
    with ByteStringHelpers
    with Waiters {

  implicit val pc = new PatienceConfig(timeout = 2.seconds)
  implicit val as  = ActorSystem()
  implicit val mat = ActorMaterializer()
  import as.dispatcher

  def bootServer(tls: Option[TlsContext] = None) = {
    val serverLogic = Flow[Pdu].map {
      case bt: BindTransceiver =>
        println(s"Server received bind $bt")
        BindTransceiverResp(
            CommandStatus.ESME_ROK, bt.sequenceNumber, None, None)
    }

    val binding = Smpp(as)
      .listen(
          "localhost", port = 0, tlsContext = tls)
      .to(Sink.foreach { conn =>
        println(s"Incoming connection from ${conn.remoteAddress}!")
        conn.flow.join(serverLogic).run()
      })
      .run()
    binding
  }

  def connectClient(connectTo: InetSocketAddress, tls: Option[TlsContext] = None) = {
    Smpp(as).connect(
      connectTo, tlsContext = tls)
  }

  def testWith(pdu: BindTransceiver, tls: Option[TlsContext]): Unit = {
    val waiter = new Waiter()
    bootServer().onSuccess {
      case ServerBinding(addr) =>
        println(s"Server successfully bound $addr")
        val flow = connectClient(addr)
        val (conn, resp) = Source.single(pdu).viaMat(flow)(Keep.right)
          .toMat(Sink.head)(Keep.both)
          .run()

        conn.onComplete(println)

        resp.onSuccess {
          case resp: BindTransceiverResp =>
            resp.sequenceNumber should be (pdu.sequenceNumber)
            waiter.dismiss()
        }
        resp.onFailure { case ex: Throwable => fail(ex)}
    }
    waiter.await()
  }

  val pduBuilder = PduBuilder()
  val bind = pduBuilder.bindTransceiver(COctetString.ascii("user"), COctetString.ascii("pass"))

  "A client" should "be able to connect to a server" in {
    testWith(bind(123), None)
  }
  "A TLS client" should "be able to connect to a TLS server" in {
    testWith(bind(456), Some(TlsContext(SSLContext.getDefault)))
  }
}
