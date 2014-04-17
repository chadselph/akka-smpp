package akkasmpp
import akka.io.{IO, Tcp}
import akka.actor.ActorSystem
import akka.pattern.ask
import akkasmpp.actors.{SmppPartials, SmppServerHandler, SmppServerConfig, SmppServer, SmppClientConfig, SmppClient}
import java.net.InetSocketAddress
import akkasmpp.actors.SmppClient.{Bind, SendMessageAck, SendMessage, Did}
import akka.util.Timeout
import scala.concurrent.duration._
import akkasmpp.protocol.{COctetString, CommandStatus, SubmitSmResp, SubmitSm, Pdu}

object Demo extends App {

  implicit val actorSystem = ActorSystem("demo")
  val manager = IO(Tcp)

  implicit val t: Timeout = 5.seconds
  import scala.concurrent.ExecutionContext.Implicits.global
  /*
    Demo server
   */

  SmppServer.run("localhost", 2775) { wire =>
    new SmppServerHandler(wire) with SmppPartials {

      override def bound = enquireLinkResponder orElse processSubmitSm

      def processSubmitSm: Receive = {
        case wire.Event(ss: SubmitSm) =>
          log.info(s"SubmitSm with TLVs of ${ss.tlvs}")
          sender ! wire.Command(SubmitSmResp(
            CommandStatus.ESME_ROK, ss.sequenceNumber, Some(new COctetString("abcde"))))
          val forwarded = ss.copy(tlvs = Nil)
      }
    }
  }


  /*
    Demo client
   */

  for (creds <- List(("smppclient1", "password"), ("user2", "pass2"))) {
    val client = actorSystem.actorOf(SmppClient.props(SmppClientConfig(new InetSocketAddress("localhost", 2775))))
    client ! Bind(creds._1, creds._2)
    val f = client ? SendMessage("this is message", Did("+15094302095"), Did("+15094302096"))
    f.mapTo[SendMessageAck].onComplete { ack =>
      println(ack)
    }
    (client ? SendMessage("hahahaa", Did("+15094302095"), Did("+44134243"))) onComplete { x =>
      println(x)
    }
  }

}
