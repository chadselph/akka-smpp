import akkasmpp.protocol.bytestrings.ReadPdu
import akkasmpp.protocol.EsmClass.{MessageType, MessagingMode}
import akkasmpp.protocol.{OctetString, COctetString, DataCodingScheme, RegisteredDelivery, NullTime, Priority, EsmClass, NumericPlanIndicator, TypeOfNumber, ServiceType, DeliverSm}
import org.scalatest.{Matchers, FlatSpec}

class ReadPduTest extends FlatSpec with Matchers {

  implicit val ce = java.nio.charset.Charset.forName("UTF-8")

  "DeliverSm" should "get parsed correctly" in {
    val deliverSm = DeliverSm(123, ServiceType.Default, TypeOfNumber.National, NumericPlanIndicator.E164, new COctetString("4413241434"),
      TypeOfNumber.National, NumericPlanIndicator.E164, new COctetString("4413241435"), EsmClass(MessagingMode.Default, MessageType.NormalMessage),
      0x34, Priority.Level0, NullTime, NullTime, RegisteredDelivery(), false, DataCodingScheme.Latin1, 0x0, 5, new OctetString("12345".getBytes), Nil)
    val bs = deliverSm.toByteString
    val parsedDeliverSm: DeliverSm = ReadPdu.readPdu(bs.iterator).asInstanceOf[DeliverSm]
    parsedDeliverSm should be (deliverSm)
  }

}
