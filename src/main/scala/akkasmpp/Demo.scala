package akkasmpp
import akka.io.{IO, Tcp}
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akkasmpp.actors.{SmppClientConfig, SmppClient}
import java.net.InetSocketAddress
import akkasmpp.actors.SmppClient.{Bind, SendMessageAck, SendMessage, Did}
import akka.util.Timeout
import scala.concurrent.duration._

object Demo extends App {

  implicit val actorSystem = ActorSystem("demo")
  val manager = IO(Tcp)

  implicit val t: Timeout = 5.seconds
  import scala.concurrent.ExecutionContext.Implicits.global

  val client = actorSystem.actorOf(SmppClient.props(SmppClientConfig(new InetSocketAddress("localhost", 12775))))
  client ! Bind("smppclient1", "password")
  val f = client ? SendMessage("this is message", Did("+15094302095"), Did("+15094302096"))
  f.mapTo[SendMessageAck].onComplete { ack =>
    println(ack)
  }
  (client ? SendMessage("hahahaa", Did("+15094302095"), Did("+44134243"))) onComplete { x =>
    println(x)
  }
}
