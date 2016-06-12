package akkasmpp.protocol

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import org.scalatest.{FunSuite, ShouldMatchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * akka stream tests
  */
class SmppPduFramingStageTest extends FunSuite with ShouldMatchers {

  val testConfig = com.typesafe.config.ConfigFactory.parseString(
    """
      |smpp.protocol {
      |  min-pdu-length = 16 # length of header-only PDU
      |  max-pdu-length = 7424
      |}
    """.stripMargin).getConfig("smpp")
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  test("we can parse a pdu even when we send it one byte at a time") {
    val flow: Flow[ByteString, Pdu, NotUsed] = Flow.fromGraph(new SmppPduFramingStage(testConfig))
    val testPdu = EnquireLinkResp(1234)
    val byteStrings = testPdu.toByteString.map(ByteString(_))
    val source = Source(byteStrings)

    val pduF = source.via(flow).runWith(Sink.head)
    Await.result(pduF, 1.seconds) should be (testPdu)

  }

}
