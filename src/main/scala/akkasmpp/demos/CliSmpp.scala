package akkasmpp.demos

import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import akka.stream.ActorMaterializer
import akkasmpp.actors.SmppClient.{Bind, SendPdu}
import akkasmpp.actors.{SmppClient, SmppClientConfig}
import akkasmpp.extensions.Smpp
import akkasmpp.protocol._
import akkasmpp.userdata.DefaultGsmCharset

import scala.concurrent.duration._

/**
 * The CLI I've always wanted for SMPP testing
 */
object CliSmpp extends App {

  implicit val actorSystem = ActorSystem("cli-smpp")
  implicit val mat = ActorMaterializer()

  val menu = """
      |To send a pdu, select the pdu type.
      | bt: BindTransmitter
      | br: BindReceiver
      | bx: BindTransceiver
      | ss: SubmitSm
      | el: EnquireLink
      | xx: Drop into a scala interptetter to create a PDU
      | q : Exit
    """.stripMargin

  def receiveCallback: SmppClient.ClientReceive = {
    case p: Pdu => GenericNack(CommandStatus.ESME_RINVSYSTYP, p.sequenceNumber)
  }
  val logger = new PduLogger {
    override def logIncoming(pdu: Pdu): Unit = println("Incoming: " + pdu)
    override def logOutgoing(pdu: Pdu): Unit = println("Sending : " + pdu)
  }

  def getDest() = COctetString.ascii("+15094302095")

  def getSrc() = COctetString.ascii("+15095456717")

  def getShortMsg() = OctetString.fromBytes(DefaultGsmCharset.encode("This was a triumph."))

  val pduBuilder = new PduBuilder()

  case class RunConfig(server: String, port: Int, ssl: Boolean) {
    val addr = new InetSocketAddress(server, port)
  }

  val rc = args match {
    case Array(server, port) if port.forall(_.isDigit) =>
      RunConfig(server, port.toInt, false)
    case Array(server, port, "--ssl") if port.forall(_.isDigit) =>
      RunConfig(server, port.toInt, true)
    case Array(server) => RunConfig(server, 2775, false)
  }

  case object UserInput
  val clientSup = actorSystem.actorOf(Props(new Actor {

    val client = SmppClient.connect(SmppClientConfig(
      rc.addr, Duration.Inf, None, None), receiveCallback, "cli-client", logger)(context, mat)

    override def supervisorStrategy = OneForOneStrategy(0) {
      case ex: Exception =>
        println(ex)
        doStop()
        Stop
    }

    override def receive: Receive = {
      case Smpp.OutgoingConnection(_, _) =>
        self ! UserInput
      case UserInput =>
        self ! Console.readLine("==> ").trim
      case "bt" =>
        client ! Bind("someasfd", "asfd", mode = SmppClient.Transmitter)
        self ! UserInput
      case "br" =>
        client ! Bind("someasfd", "asfd", mode = SmppClient.Receiver)
        self ! UserInput
      case "bx" =>
        client ! Bind("someasfd", "asfd", mode = SmppClient.Transceiver)
        self ! UserInput
      case "ss" =>
        client ! SendPdu(pduBuilder.submitSm(destinationAddr = getDest(), sourceAddr = getSrc(), shortMessage = getShortMsg()))
        self ! UserInput
      case "el" =>
        client ! SendPdu(EnquireLink)
        self ! UserInput
      case "xx" => self ! UserInput
      case "" => self ! UserInput
      case _: String => println(menu)
        self ! UserInput
      case "q" => doStop()
    }

    def doStop(): Unit = {
      println("shutting down...")
      context.stop(self)
      mat.shutdown()
      actorSystem.shutdown()

    }
  }))

  println(menu)

}
