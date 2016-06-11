package akkasmpp

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem, Terminated}
import akka.io.{IO, Tcp}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akkasmpp.actors.SmppClient.SendEnquireLink
import akkasmpp.actors.SmppServer.Disconnected
import akkasmpp.actors._
import akkasmpp.protocol._
import akkasmpp.protocol.auth.{BindAuthenticator, BindRequest}

import scala.concurrent.Future
import scala.concurrent.duration._

object Demo extends App {

  implicit val actorSystem = ActorSystem("demo")
  implicit val materializer = ActorMaterializer()
  implicit val ec = actorSystem.dispatcher
  val manager = IO(Tcp)

  val pduBuilder = new PduBuilder()

  implicit val t: Timeout = 5.seconds

  /*
  Http(actorSystem).bind("", port=1025).runForeach({ connection =>
    connection.flow
  })
  */

  actorSystem.actorOf(SmppServer.props(SmppServerConfig(new InetSocketAddress("0.0.0.0", 2775)), new SmppServerHandler {


    override val bindAuthenticator: BindAuthenticator = new BindAuthenticator {
      override def allowBind(bindRequest: BindRequest) = {
        Future.successful(
          bindRequest.respondOk(Some(COctetString.utf8("akka-smpp-demo")))
        )
      }
    }


    // XXX: split out into bound transmit vs bound receive
    override def bound(connection: ActorRef): Receive = {
      case el: EnquireLink => connection ! EnquireLinkResp(el.sequenceNumber)
      case submit: SubmitSm => connection ! SubmitSmResp(CommandStatus.ESME_ROK, submit.sequenceNumber, Some(COctetString.utf8("1234-asdf")))
      case SendEnquireLink => connection ! EnquireLink(sequenceNumberGen.next)
      case Terminated(`connection`) | Disconnected =>
        context.stop(self)
      case x => println("got " + x)
    }
  }, printlnPduLogger("server")))

  // Demo Client

  /*
  val myClient = SmppClient.connect(SmppClientConfig(new InetSocketAddress("localhost", 2775), 30.seconds, None, Some(SmppClient.Bind("hello", "bud"))), {
    case EnquireLink(sn) => EnquireLinkResp(sn)
  },
  "client", printlnPduLogger("client"))

  Thread.sleep(3000)
  println(myClient)
  val submitSmF = myClient ? SendPdu(pduBuilder.submitSm(sourceAddr = COctetString.ascii("+15094302094"),
    destinationAddr = COctetString.ascii("+181834234134"), shortMessage = OctetString.fromBytes(Array[Byte](0,0,0))))

  submitSmF.onComplete(println)
  */


  /*
  c ? Bind("any", "any") onComplete { x =>
    val f = c ? SendRawPdu(SubmitSm(_, "tyntec", TypeOfNumber.International, NumericPlanIndicator.E164, "15094302095", TypeOfNumber.International,
      NumericPlanIndicator.E164, "15094302095", EsmClass(MessagingMode.Default, MessageType.NormalMessage), 0x0,
      Priority.Level0, NullTime, NullTime, RegisteredDelivery(0x0.toByte), false, DataCodingScheme.SmscDefaultAlphabet, 0x0, 0x0, OctetString.empty, Nil))

    f.onComplete {
      case Success(SubmitSmResp(commandStatus, _, _)) => println(s"command status was $commandStatus")
      case other => println(s"Unexpected response: $other")
    }
  }
    Demo client

  for (creds <- List(("smppclient1", "password"), ("user2", "pass2"))) {
    val client = actorSystem.actorOf(SmppClient.props(SmppClientConfig(new InetSocketAddress("localhost", 2775)), {
      case d: DeliverSm =>
        println(s"Incoming message $d")
        DeliverSmResp(CommandStatus.ESME_ROK, d.sequenceNumber, None)
    }))
    client ! Bind(creds._1, creds._2)
    val f = client ? SendMessage("this is message", Did("+15094302095"), Did("+15094302096"))
    f.mapTo[SendMessageAck].onComplete { ack =>
      println(ack)
    }
  }

   */

  def printlnPduLogger(prefix: String) = new PduLogger {
    override def logOutgoing(pdu: Pdu): Unit = println(s"$prefix OUT: $pdu")

    override def logIncoming(pdu: Pdu): Unit = println(s"$prefix IN : $pdu")
  }

}
