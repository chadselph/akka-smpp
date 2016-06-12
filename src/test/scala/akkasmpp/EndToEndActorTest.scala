package akkasmpp

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import akkasmpp.actors.SmppClient.ClientReceive
import akkasmpp.actors.{SmppClient, SmppClientConfig, SmppServer, SmppServerConfig, SmppServerHandler}
import akkasmpp.extensions.Smpp.{OutgoingConnection, ServerBinding}
import akkasmpp.protocol.auth.{BindAuthenticator, BindRequest, BindResponse}
import akkasmpp.protocol.{BindTransceiverResp, COctetString, CommandStatus, DeliverSm, DeliverSmResp, EnquireLink, EnquireLinkResp, OctetString, PduBuilder, SubmitSm, SubmitSmResp}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.{Await, Future, Promise}

/**
  * End to end tests for the actors module.
  */
class EndToEndActorTest
    extends TestKit(ActorSystem("test"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val materializer = ActorMaterializer()
  implicit val ec           = system.dispatcher
  val pduBuilder            = PduBuilder()

  val inetSocketAddress = new InetSocketAddress("127.0.0.1", 0)
  val serverProps = SmppServer.props(
      config = new SmppServerConfig(bindAddr = inetSocketAddress),
      handlerSpec = new SmppServerHandler {
        override def bound(connection: ActorRef): Receive = {
          case el: EnquireLink =>
            connection ! EnquireLinkResp(el.sequenceNumber)
          case sm: SubmitSm =>
            connection ! SubmitSmResp(CommandStatus.ESME_ROK, sm.sequenceNumber, None)
        }

        override def bindAuthenticator: BindAuthenticator = {
          new BindAuthenticator {
            override def allowBind(
                br: BindRequest,
                _l: InetSocketAddress,
                _r: InetSocketAddress): Future[BindResponse] = {
              if (br.systemId == COctetString.ascii("login"))
                Future.successful(br.respondOk())
              else Future.successful(br.respond(CommandStatus.ESME_RINVSYSID))
            }
          }
        }
      },
      pduLogger = Demo.printlnPduLogger("Test-server")
  )

  def clientProps(addr: InetSocketAddress, clientReceive: ClientReceive) =
    SmppClient.props(SmppClientConfig(addr),
                     clientReceive,
                     pduLogger = Demo.printlnPduLogger("test-client"))

  def setupServer(): Future[(ActorRef, ServerBinding)] = {
    val p = Promise[(ActorRef, ServerBinding)]()
    system.actorOf(
        Props(new Actor {
      val server = context.actorOf(serverProps, "server-test")
      override def receive: Receive = {
        case s: ServerBinding =>
          p.success((server, s))
      }
    }))
    p.future
  }

  def connectClient(
      addr: InetSocketAddress): Future[(ActorRef, OutgoingConnection)] = {
    val client = Promise[(ActorRef, OutgoingConnection)]()
    system.actorOf(Props(new Actor {
      val ref = context.actorOf(clientProps(addr, {
        case resp: DeliverSm =>
          DeliverSmResp(CommandStatus.ESME_ROK, resp.sequenceNumber, None)
      }))
      override def receive: Receive = {
        case oc: OutgoingConnection => client.success((ref, oc))
      }
    }))
    client.future
  }

  import scala.concurrent.duration._
  implicit val timeout: akka.util.Timeout = 3.seconds
  "this server actor" must {

    val (server, binding) = Await.result(setupServer(), 3.seconds)
    // start client

    "reject a bind request with the wrong sys id" in {
      val (client, _) =
        Await.result(connectClient(binding.localAddress), 3.seconds)
      client ! SmppClient.Bind("login2", "")
      val resp = expectMsgClass(classOf[BindTransceiverResp])
      resp.commandStatus should be(CommandStatus.ESME_RINVSYSID)
    }

    "accept a bind request with the right sysid" in {
      val (client, _) =
        Await.result(connectClient(binding.localAddress), 3.seconds)
      client ! SmppClient.Bind("login", "")
      val resp = expectMsgClass(classOf[BindTransceiverResp])
      resp.commandStatus should be(CommandStatus.ESME_ROK)
    }
    "accept a submit_sm after we are bound" in {
      val (client, _) =
        Await.result(connectClient(binding.localAddress), 3.seconds)
      client ! SmppClient.Bind("login", "")
      val resp = expectMsgClass(classOf[BindTransceiverResp])
      resp.commandStatus should be(CommandStatus.ESME_ROK)
      client ! SmppClient.SendPdu(
          pduBuilder.submitSm(shortMessage = OctetString(0, 0, 0, 0),
                              sourceAddr = COctetString.ascii("413241"),
                              destinationAddr = COctetString.ascii("1245")))
      val resp2 = expectMsgClass(classOf[SubmitSmResp])
      resp2.messageId should be (None)
      resp2.commandStatus should be (CommandStatus.ESME_ROK)
    }
  }
}
