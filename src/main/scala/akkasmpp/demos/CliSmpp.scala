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
    // ack messages so they don't repeat
    case sms: DeliverSm => DeliverSmResp(CommandStatus.ESME_ROK, sms.sequenceNumber, None )
    case p: Pdu => GenericNack(CommandStatus.ESME_RINVSYSTYP, p.sequenceNumber)
  }

  val logger = new PduLogger {
    override def logIncoming(pdu: Pdu): Unit = println("Incoming: " + pdu)
    override def logOutgoing(pdu: Pdu): Unit = println("Sending : " + pdu)
  }

  def getDest(num: String= "+15094302095") = COctetString.ascii(num)


  def getShortMsg(msg: String = "This was a triumph.") = OctetString.fromBytes(DefaultGsmCharset.encode(msg))

  val pduBuilder = new PduBuilder()

  case class RunConfig(
      server: String = "localhost",
      port: Int = 2775,
      ssl: Boolean= false, 
      systemId: String = "someasfd",
      password: String = "asfd",
      shortCode: COctetString = COctetString.ascii("+12512161914")) {
    val addr = new InetSocketAddress(server, port)
  }

  val rc = args match {
    // Preserving compatibility
    case Array(server, port, "--ssl") if port.forall(_.isDigit) =>
      RunConfig(server, port.toInt, true)
    // Adding sysID, pwd and shortcode, so this can be bundled in a test tool and used on 
    // multiple binds without recompiling. 
    case Array(server, port, systemId, password, shortCode) if port.forall(_.isDigit) =>
      RunConfig(server, port.toInt, false, systemId, password, COctetString.ascii(shortCode))
    case Array(server, port, systemId, password) if port.forall(_.isDigit) =>
      RunConfig(server, port.toInt, false, systemId, password)
    case Array(server, port) if port.forall(_.isDigit) =>
      RunConfig(server, port.toInt, false )
    case Array(server) => RunConfig(server)
    case Array() => RunConfig()
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
        client ! Bind(rc.systemId, rc.password, mode = SmppClient.Transmitter)
        self ! UserInput
      case "br" =>
        client ! Bind(rc.systemId, rc.password, mode = SmppClient.Receiver)
        self ! UserInput
      case "bx" =>
        client ! Bind(rc.systemId, rc.password, mode = SmppClient.Transceiver)
        self ! UserInput
      case "ss" =>
        val dest = Console.readLine("To? ").trim
        val msg = Console.readLine("Msg? ").trim
        client ! SendPdu(pduBuilder.submitSm(destinationAddr = getDest(dest), sourceAddr = rc.shortCode,
          shortMessage = getShortMsg(msg)
          ))
        self ! UserInput
      case "el" =>
        client ! SendPdu(EnquireLink)
        self ! UserInput
      case "xx" => self ! UserInput
      case "" => self ! UserInput
      case "q" => doStop()
      case _: String => println(menu)
        self ! UserInput
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

