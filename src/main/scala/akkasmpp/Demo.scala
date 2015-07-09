package akkasmpp

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.io.{IO, Tcp}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akkasmpp.actors.SmppClient.SendEnquireLink
import akkasmpp.actors.{SmppServer, SmppServerConfig, SmppServerHandler}
import akkasmpp.protocol._

import scala.concurrent.duration._

object Demo extends App {

  implicit val actorSystem = ActorSystem("demo")
  implicit val materializer = ActorMaterializer()
  val manager = IO(Tcp)

  implicit val t: Timeout = 5.seconds

  Http(actorSystem).bind("", port=1025).runForeach({ connection =>
    connection.flow
  })

  actorSystem.actorOf(SmppServer.props(SmppServerConfig(new InetSocketAddress("0.0.0.0", 2775)), new SmppServerHandler {

    context.system.scheduler.schedule(0.seconds, 10.seconds, self, SendEnquireLink)(context.dispatcher)

    // XXX: split out into bound transmit vs bound receive
    override def bound(connection: ActorRef): Receive = {
      case el: EnquireLink => connection ! EnquireLinkResp(el.sequenceNumber)
      case submit: SubmitSm => connection ! SubmitSmResp(CommandStatus.ESME_ROK, submit.sequenceNumber, Some(COctetString.utf8("1234-asdf")))
      case SendEnquireLink => connection ! EnquireLink(sequenceNumberGen.next)
      case x => println("got " + x)
    }
  }, new PduLogger {
    override def logOutgoing(pdu: Pdu): Unit = println(s"OUT: $pdu")
    override def logIncoming(pdu: Pdu): Unit = println(s"IN : $pdu")
  }))

  /*
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

  val c = SmppClient.connect(SmppClientConfig(new InetSocketAddress("ec2-184-73-153-156.compute-1.amazonaws.com", 2775)), {
    case d: SmscRequest => GenericNack(CommandStatus.ESME_RINVCMDID, d.sequenceNumber)
  }, "client")

  implicit def str2CoctetString(s: String): COctetString = COctetString.utf8(s)

  c ? Bind("any", "any") onComplete { x =>
    val f = c ? SendRawPdu(SubmitSm(_, "tyntec", TypeOfNumber.International, NumericPlanIndicator.E164, "15094302095", TypeOfNumber.International,
    NumericPlanIndicator.E164, "15094302095", EsmClass(MessagingMode.Default, MessageType.NormalMessage), 0x0,
    Priority.Level0, NullTime, NullTime, RegisteredDelivery(0x0.toByte), false, DataCodingScheme.SmscDefaultAlphabet, 0x0, 0x0, OctetString.empty, Nil))

    f.onComplete {
      case Success(SubmitSmResp(commandStatus, _, _)) => println(s"command status was $commandStatus")
      case other => println(s"Unexpected response: $other")
    }
  }
   */

}
