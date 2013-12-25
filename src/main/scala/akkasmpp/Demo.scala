package akkasmpp
import akka.io.{IO, Tcp}
import akka.actor.{Props, ActorSystem}
import akkasmpp.actors.SmppClient
import java.net.InetSocketAddress

object Demo extends App {

  implicit val actorSystem = ActorSystem("demo")
  val manager = IO(Tcp)

  val client = actorSystem.actorOf(Props(new SmppClient(new InetSocketAddress("localhost", 2775))))

}
