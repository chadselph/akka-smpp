package akkasmpp.protocol.bytestrings

import akka.util.{ByteIterator, ByteStringBuilder}
import java.nio.charset.Charset
import akkasmpp.protocol.CommandId.CommandId
import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.protocol.TypeOfNumber.TypeOfNumber
import akkasmpp.protocol.NumericPlanIndicator.NumericPlanIndicator
import akkasmpp.protocol.Priority.Priority
import akkasmpp.protocol.DataCodingScheme.DataCodingScheme
import akkasmpp.protocol.MessageState.MessageState
import akkasmpp.protocol.ServiceType.ServiceType
import akkasmpp.protocol.EsmClass.EsmClass
import akkasmpp.protocol.{Tlv, RegisteredDelivery, AbsoluteTimeFormat, RelativeTimeFormat, NullTime, TimeFormat}

/**
 * Helpers to parse and encode SMPP PDUs into akka ByteStrings
 */
object SmppByteString {

  // hardcoding because SMPP is BIG_ENDIAN
  implicit val byteorder = java.nio.ByteOrder.BIG_ENDIAN

  implicit class Builder(val bsb: ByteStringBuilder) extends AnyVal {

    /**
     * Puts the string into the buffer followed by a NULL byte
     * @param s String to send
     * @param charset Encoding to use
     */
    def putCOctetString(s: String)(implicit charset: Charset): bsb.type = putCOctetString(s.getBytes(charset))

    def putCOctetString(b: Array[Byte]): bsb.type = {
      putOctetString(b)
      bsb.putByte(0)
    }

    /**
     * Puts bytes of a string into the buffer without a NULL byte
     * @param s String to send
     * @param charset Encoding to use
     * @return
     */
    def putOctetString(s: String)(implicit charset: Charset): bsb.type = putOctetString(s.getBytes(charset))

    def putOctetString(b: Array[Byte]): bsb.type = bsb.putBytes(b)

    private def putEnumByte(e: Enumeration#Value) = bsb.putByte(e.id.toByte)
    private def putEnumInt(e: Enumeration#Value) = bsb.putInt(e.id)

    // simple wrappers
    def putCommandId = putEnumInt(_: CommandId)
    def putCommandStatus = putEnumInt(_: CommandStatus)
    def putTypeOfNumber = putEnumByte(_: TypeOfNumber)
    def putNumberPlanIndicator = putEnumByte(_: NumericPlanIndicator)
    def putPriority = putEnumByte(_: Priority)
    def putDataCodingScheme = putEnumByte(_: DataCodingScheme)
    def putMessageState = putEnumByte (_: MessageState)
    // more complicated
    def putServiceType(st: ServiceType) = bsb.putCOctetString(st.toString)(Charset.forName("ASCII"))
    def putEsmClass(esm: EsmClass) = ???
    def putTime(t: TimeFormat) = t match {
      case NullTime => bsb.putByte(0)
      case RelativeTimeFormat(y, mon, d, h, min, s) => ???
      case AbsoluteTimeFormat(y, mon, d, h, min, s) => ???
    }
    def putRegisteredDelivery(rd: RegisteredDelivery.RegisteredDelivery) = rd match {
      case RegisteredDelivery.RegisteredDelivery(smscDelivery, smseAck, imNotif) =>
        val combined = (smscDelivery.id.toByte | smseAck.id.toByte | imNotif.id.toByte).toByte
        bsb.putByte(combined)
    }
    def putTlv(tlv: Tlv) = {
      bsb.putShort(tlv.tag)
      bsb.putShort(tlv.length)
      bsb.putBytes(tlv.value)
    }


  }

  implicit class Iterator(val bi: ByteIterator) extends AnyVal {

  }
}
