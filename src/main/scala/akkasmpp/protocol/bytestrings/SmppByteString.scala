package akkasmpp.protocol.bytestrings

import akka.util.{ByteIterator, ByteStringBuilder}
import java.nio.charset.Charset
import akkasmpp.protocol.{ServiceType, MessageState, DataCodingScheme, Priority, NumericPlanIndicator, TypeOfNumber, CommandStatus, CommandId, EsmClass, Tlv, RegisteredDelivery, AbsoluteTimeFormat, RelativeTimeFormat, NullTime, TimeFormat}
import akkasmpp.protocol.CommandId.CommandId
import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.protocol.TypeOfNumber.TypeOfNumber
import akkasmpp.protocol.NumericPlanIndicator.NumericPlanIndicator
import akkasmpp.protocol.Priority.Priority
import akkasmpp.protocol.DataCodingScheme.DataCodingScheme
import akkasmpp.protocol.MessageState.MessageState
import akkasmpp.protocol.ServiceType.ServiceType

/**
 * Helpers to parse and encode SMPP PDUs into akka ByteStrings
 */
object SmppByteString {

  // hardcoding because SMPP is BIG_ENDIAN
  implicit val byteorder = java.nio.ByteOrder.BIG_ENDIAN
  val ascii = java.nio.charset.Charset.forName("ASCII")

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
    def putServiceType(st: ServiceType) = bsb.putCOctetString(st.toString)(ascii)
    def putEsmClass(esm: EsmClass) = {
      val bits = esm.features.toBitMask(0) | esm.messageType.id | esm.messagingMode.id
      bsb.putByte(bits.toByte)
    }
    def putTime(t: TimeFormat) = t match {
      case NullTime => bsb.putByte(0)
      case RelativeTimeFormat(y, mon, d, h, min, s) => ??? // XXX: maybe switch to Joda-time? Doesn't seem too important
      case AbsoluteTimeFormat(y, mon, d, h, min, s) => ???
    }
    def putRegisteredDelivery(rd: RegisteredDelivery) = rd match {
      case RegisteredDelivery(smscDelivery, smseAck, imNotif) =>
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

    def getCOctetString: Array[Byte] = (bi takeWhile (_ != '\0')).toArray
    def getOctetString(n: Int): Array[Byte] = (bi take n).toArray
    def getCOctetStringMaybe: Option[Array[Byte]] = {
      if (bi.isEmpty) None
      else Some(getCOctetString)
    }


    /*
      XXX: some concern here around how to react to invalid bytes...
      this approach will basically just throw an exception.
     */
    def getCommandId = CommandId(bi.getByte)
    def getCommandStatus = CommandStatus(bi.getInt)
    def getTypeOfNumber = TypeOfNumber(bi.getByte)
    def getNumericPlanIndicator = NumericPlanIndicator(bi.getByte)
    def getPriority = Priority(bi.getByte)
    def getDataCodingScheme = DataCodingScheme(bi.getByte)
    def getMessageState = MessageState(bi.getByte)
    def getServiceType = ServiceType.withName(new String(bi.getCOctetString, ascii))
    def getEsmClass = {
      val b = bi.getByte
      // XXX: make this into an unapply destructor?
      val messagingMode = EsmClass.MessagingMode(b & 3)
      val messagingType = EsmClass.MessageType(b & 60)
      val features = EsmClass.Features.ValueSet.fromBitMask(Array(b & 192L))
      EsmClass(messagingMode, messagingType, features.toSeq: _*)
    }

    def getTime = {
      // XXX: parse time formats
      val time = bi.getCOctetString
      NullTime
    }

    def getRegisteredDelivery = {
      val b = bi.getByte
      RegisteredDelivery(
        RegisteredDelivery.SmscDelivery(b & 3),
        RegisteredDelivery.SmeAcknowledgement(b & 12),
        RegisteredDelivery.IntermediateNotification(b & 16)
      )
    }

    def getTlv = {
      val tag = bi.getShort
      val len = bi.getShort
      val value = new Array[Byte](len)
      bi.getBytes(value)
      Tlv(tag, len, value)
    }

    def getTlvs: List[Tlv] = {
      if (bi.isEmpty) Nil
      else bi.getTlv :: getTlvs
    }
  }
}
