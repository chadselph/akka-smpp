package akkasmpp
import java.net.InetSocketAddress

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import akkasmpp.actors.SmppClient.{Bind, SendRawPdu}
import akkasmpp.actors.{SmppClient, SmppClientConfig, SmppPartials, SmppServer, SmppServerHandler}
import akkasmpp.protocol.EsmClass.{MessageType, MessagingMode}
import akkasmpp.protocol.{COctetString, CommandStatus, DataCodingScheme, EsmClass, GenericNack, NullTime, NumericPlanIndicator, OctetString, Priority, RegisteredDelivery, SmscRequest, SubmitSm, SubmitSmResp, TypeOfNumber}

import scala.concurrent.duration._
import scala.util.Success

object Demo extends App {

  implicit val actorSystem = ActorSystem("demo")
  val manager = IO(Tcp)

  implicit val t: Timeout = 5.seconds
  import scala.concurrent.ExecutionContext.Implicits.global
  /*
    Demo server
   */

  SmppServer.run("localhost", 2775) { (wire, connection) =>
    new SmppServerHandler(wire, connection) with SmppPartials {

      override def bound = enquireLinkResponder orElse processSubmitSm

      def processSubmitSm: Receive = {
        case wire.Event(ss: SubmitSm) =>
          log.info(s"SubmitSm with TLVs of ${ss.tlvs}")
          sender ! wire.Command(SubmitSmResp(
            CommandStatus.ESME_ROK, ss.sequenceNumber, Some(new COctetString("abcde"))))
      }
    }
  }


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
    (client ? SendMessage("hahahaa", Did("+15094302095"), Did("+44134243"))) onComplete { x =>
      println(x)
    }

    (client ? SendRawPdu(EnquireLink)) onComplete { x =>
      println(x)
    }
  }
   */

  val c = SmppClient.connect(SmppClientConfig(new InetSocketAddress("ec2-184-73-153-156.compute-1.amazonaws.com", 2775)), {
    case d: SmscRequest => GenericNack(CommandStatus.ESME_RINVCMDID, d.sequenceNumber)
  }, "client")

  implicit def str2CoctetString(s: String): COctetString = new COctetString(s)(java.nio.charset.Charset.forName("ASCII"))

  c ? Bind("any", "any") onComplete { x =>
    val f = c ? SendRawPdu(SubmitSm(_, "tyntec", TypeOfNumber.International, NumericPlanIndicator.E164, "15094302095", TypeOfNumber.International,
    NumericPlanIndicator.E164, "15094302095", EsmClass(MessagingMode.Default, MessageType.NormalMessage), 0x0,
    Priority.Level0, NullTime, NullTime, RegisteredDelivery(0x0.toByte), false, DataCodingScheme.SmscDefaultAlphabet, 0x0, 0x0, OctetString.empty, Nil))

    f.onComplete {
      case Success(SubmitSmResp(commandStatus, _, _)) => println(s"command status was $commandStatus")
    }
  }

  actorSystem.actorOf(Props(new Actor() {
    override def receive: Actor.Receive = ???
  }))



}
