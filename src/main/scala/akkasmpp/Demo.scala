package akkasmpp
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.io.{IO, Tcp}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, Flow}
import akka.stream.stage.{Context, StageState, StatefulStage, SyncDirective}
import akka.util.Timeout
import akkasmpp.extensions.Smpp
import akkasmpp.protocol._
import akkasmpp.protocol.auth.{BindAuthenticator, BindRequest}

import scala.concurrent.Await
import scala.concurrent.duration._

object Demo extends App {

  implicit val actorSystem = ActorSystem("demo")
  implicit val materializer = ActorMaterializer()
  val manager = IO(Tcp)

  implicit val t: Timeout = 5.seconds
  /*
    Demo server
   */

  /*
  SmppServer.run("localhost", 2775) { (wire, connection) =>
    new SmppServerHandler(wire, connection) with SmppPartials {

      override def bound = enquireLinkResponder orElse processSubmitSm

      def processSubmitSm: Receive = {
        case wire.Event(ss: SubmitSm) =>
          log.info(s"SubmitSm with TLVs of ${ss.tlvs}")
          sender ! wire.Command(SubmitSmResp(
            CommandStatus.ESME_ROK, ss.sequenceNumber, Some(COctetString.ascii("abcde"))))
      }
    }
  }
  */

  Http(actorSystem).bind("", port=1025).runForeach({ connection =>
    connection.flow
  })

  val pduEcho = Flow[Pdu].map(identity)
  val handler = Flow[Pdu].map(x => {
    println("<-- " + x)
    val resp = x match {
      case BindTransceiver(seqN, _, _, _, _, _, _, _) =>
        BindTransceiverResp(CommandStatus.ESME_ROK, seqN, None, None)
      case BindTransmitter(seqN, _, _, _, _, _, _, _) =>
        BindTransmitterResp(CommandStatus.ESME_ROK, seqN, None, None)
      case BindReceiver(seqN, _, _, _, _, _, _, _) =>
        BindReceiverResp(CommandStatus.ESME_ROK, seqN, None, None)
      case EnquireLink(seqN) => EnquireLinkResp(seqN)
      case submitSm: SubmitSm =>
        SubmitSmResp(CommandStatus.ESME_ROK, submitSm.sequenceNumber, Some(COctetString.ascii("abcde-asdf-asdf")))
      case _ => GenericNack(CommandStatus.ESME_RINVOPTPARAMVAL, x.sequenceNumber)
    }
    println("--> " + resp)
    resp
  })

  class SmppStage(authenticator: BindAuthenticator) extends StatefulStage[Pdu, Pdu] {

    override def initial: StageState[Pdu, Pdu] = new StageState[Pdu, Pdu] {
      override def onPush(elem: Pdu, ctx: Context[Pdu]): SyncDirective = {
        elem match {
          case bl: BindLike =>
            val bindResponse = authenticator.allowBind(BindRequest.fromBindLike(bl))
            import scala.concurrent.duration._
            val resp = Await.result(bindResponse, 2.seconds) // XXX: is StatefulStage threadsafe? I don't want to block.
            emit(List(resp.asInstanceOf[Pdu]).iterator, ctx, bound)
        }
      }
    }

    def bound: StageState[Pdu, Pdu] = new StageState[Pdu, Pdu] {
      override def onPush(elem: Pdu, ctx: Context[Pdu]): SyncDirective = {
        ctx.push(elem)
      }
    }
  }



  val serverBindings = Smpp(actorSystem).listen(interface = "", port = 2775)
  serverBindings.runForeach { connection =>
    println(connection.remoteAddress)
    connection.handle(handler)
    val f = Source.single(EnquireLink(1337)).via(connection.flow).runWith(Sink.head)
    f.onComplete { println }(actorSystem.dispatcher)
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
