import akkasmpp.protocol.bytestrings.ReadPdu
import akkasmpp.protocol.EsmClass.{MessageType, MessagingMode}
import akkasmpp.protocol.{CommandStatus, CommandId, BindTransceiverResp, Pdu, OctetString, COctetString, DataCodingScheme, RegisteredDelivery, NullTime, Priority, EsmClass, NumericPlanIndicator, TypeOfNumber, ServiceType, DeliverSm}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Inside, Matchers, FlatSpec}
import scala.language.reflectiveCalls

@RunWith(classOf[JUnitRunner])
class ReadPduTest extends FlatSpec with Matchers with ByteStringHelpers with GeneratorDrivenPropertyChecks with Inside {

  implicit val ce = java.nio.charset.Charset.forName("UTF-8")

  "DeliverSm" should "get parsed correctly" in {
    val deliverSm = DeliverSm(123, ServiceType.Default, TypeOfNumber.National, NumericPlanIndicator.E164, COctetString.ascii("4413241434"),
      TypeOfNumber.National, NumericPlanIndicator.E164, COctetString.ascii("4413241435"), EsmClass(MessagingMode.Default, MessageType.NormalMessage),
      0x34, Priority.Level0, NullTime, NullTime, RegisteredDelivery(), false, DataCodingScheme.Latin1, 0x0, 5, OctetString.fromBytes("12345".getBytes), Nil)
    val bs = deliverSm.toByteString
    val parsedDeliverSm: DeliverSm = ReadPdu.readPdu(bs.iterator).asInstanceOf[DeliverSm]
    inside (parsedDeliverSm) {
      case DeliverSm(seqN, serType, srcTon, srcNpi, srcAddr, dstTon, dstNpi, dstAddr,
        esm, protocol, priority,scheduledDelivery, validity, registeredDelivery,
        replaceIfPrsent, dcs, defaultMsdId, smLen, msg, tlvs) =>

        seqN should be (deliverSm.sequenceNumber)
        serType should be (deliverSm.serviceType)
        srcTon should be (deliverSm.sourceAddrTon)
        srcNpi should be (deliverSm.sourceAddrNpi)
        srcAddr should be (deliverSm.sourceAddr)
        dstTon should be (deliverSm.destAddrTon)
        dstNpi should be (deliverSm.destAddrNpi)
        dstAddr should be (deliverSm.destinationAddr)
        replaceIfPrsent should be (deliverSm.replaceIfPresentFlag)
        dcs should be (deliverSm.dataCoding)
        defaultMsdId should be (deliverSm.smDefaultMsgId)
        smLen should be (deliverSm.smLength)
        msg should be (deliverSm.shortMessage)
        tlvs should be (deliverSm.tlvs)

    }
    parsedDeliverSm should be(deliverSm)
  }


  "bodyless bind_transceiver_resp" should "parse correctly" in {
    withByteString {
      bsb =>
        bsb.putBytes(Array[Byte](0, 0, 0, 16, -128, 0, 0, 9, 0, 0, 0, 15, 0, 0, 0, 0))
    } andThenCheck {
      bi =>
        val bindResp = Pdu.fromBytes(bi).asInstanceOf[BindTransceiverResp]
        bindResp.commandLength should be(16)
        bindResp.commandId should be(CommandId.bind_transceiver_resp)
        bindResp.commandStatus should be(CommandStatus.ESME_RINVSYSID)
        bindResp.sequenceNumber should be(0)
        bindResp.systemId should be(None)
    }
  }

  "bind_transceiver_resp with body" should "parse correctly" in {
    withByteString {
      bsb =>
        bsb.putBytes(Array[Byte](0, 0, 0, 20, -128, 0, 0, 9, 0, 0, 0, 0, 0, 0, 0, 0, 97, 98, 99, 0))
    } andThenCheck {
      bi =>
        val bindResp = Pdu.fromBytes(bi).asInstanceOf[BindTransceiverResp]
        bindResp.commandLength should be(20)
        bindResp.commandId should be(CommandId.bind_transceiver_resp)
        bindResp.commandStatus should be(CommandStatus.ESME_ROK)
        bindResp.sequenceNumber should be(0)
        bindResp.systemId should be(Some(COctetString.ascii("abc")))
    }
  }

  "pdu round trip" should "always get back from where we started" in {
    forAll(PduGens.pduGen) { pdu =>
      ReadPdu.readPdu(pdu.toByteString.iterator) should equal (pdu)
    }
  }
}
