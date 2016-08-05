package akkasmpp.protocol.bytestrings

import akka.util.{ByteString, ByteIterator, ByteStringBuilder}
import akkasmpp.protocol.CommandId.CommandId
import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.protocol.DataCodingScheme.DataCodingScheme
import akkasmpp.protocol.MessageState.MessageState
import akkasmpp.protocol.NumericPlanIndicator.NumericPlanIndicator
import akkasmpp.protocol.Priority.Priority
import akkasmpp.protocol.ServiceType.ServiceType
import akkasmpp.protocol.TypeOfNumber.TypeOfNumber
import akkasmpp.protocol.{COctetString, CommandId, CommandStatus, DataCodingScheme, EsmClass, MessageState, NullTime, NumericPlanIndicator, OctetString, OpaqueTimeFormat, Priority, RegisteredDelivery, Tag, TimeFormat, Tlv, TypeOfNumber}

/**
 * Helpers to parse and encode SMPP PDUs into akka ByteStrings
 */
object SmppByteString {

  // hardcoding because SMPP is BIG_ENDIAN
  implicit val byteorder = java.nio.ByteOrder.BIG_ENDIAN
  implicit val ascii = java.nio.charset.Charset.forName("UTF-8")

  implicit class Builder(val bsb: ByteStringBuilder) extends AnyVal {

    /**
     * Puts the string into the buffer followed by a NULL byte
     * @param c COctetString to send
     */

    def putCOctetString(c: COctetString) = {
      bsb.append(c.data)
      bsb.putByte(0)
    }

    /**
     * Puts bytes of a string into the buffer without a NULL byte
     * @return
     */
    def putOctetString(b: OctetString) = bsb.append(b.data)

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
    def putServiceType(st: ServiceType) = bsb.putCOctetString(st)
    def putEsmClass(esm: EsmClass) = {
      val bits = esm.features.toBitMask(0) | esm.messageType.id | esm.messagingMode.id
      bsb.putByte(bits.toByte)
    }
    def putTime(t: TimeFormat) = {
      bsb.putCOctetString(t.serialize)
    }
    def putRegisteredDelivery(rd: RegisteredDelivery) = rd match {
      case RegisteredDelivery(smscDelivery, smseAck, imNotif) =>
        val combined = (smscDelivery.id.toByte | smseAck.id.toByte | imNotif.id.toByte).toByte
        bsb.putByte(combined)
    }
    def putTlv(tlv: Tlv) = {
      bsb.putShort(tlv.tag.id.toShort)
      bsb.putShort(tlv.value.size.toShort)
      bsb.putOctetString(tlv.value)
    }

  }

  implicit class Iterator(val bi: ByteIterator) extends AnyVal {

    def getCOctetString: COctetString = {
      //(bi takeWhile (_ != 0)).toArray
      val bsb = new ByteStringBuilder
      var b: Byte = bi.getByte
      while(b != 0) {
        bsb.putByte(b)
        b = bi.getByte
      }
      new COctetString(bsb.result())
    }
    def getOctetString(n: Int): OctetString = {
      // TEMP bitwise HACK
      val ba = new Array[Byte](n & 0xff)
      bi.getBytes(ba)
      new OctetString(ByteString.fromArray(ba))
    }
    def getCOctetStringMaybe: Option[COctetString] = {
      if (bi.isEmpty) None
      else Some(getCOctetString)
    }


    /*
      XXX: some concern here around how to react to invalid bytes...
      this approach will basically just throw an exception.
     */
    def getCommandId = CommandId(bi.getInt)
    def getCommandStatus = CommandStatus.getOrInvalid(bi.getInt)
    def getTypeOfNumber = TypeOfNumber(bi.getByte)
    def getNumericPlanIndicator = NumericPlanIndicator(bi.getByte)
    def getPriority = Priority.getOrInvalid(bi.getByte)
    def getDataCodingScheme = DataCodingScheme.getOrInvalid(bi.getByte & 0xf)
    def getMessageState = MessageState(bi.getByte)
    def getServiceType = bi.getCOctetString
    def getEsmClass = EsmClass(bi.getByte)

    def getSmLength = bi.getByte & 0xff

    def getTime = {
      // XXX: parse time formats
      val time = bi.getCOctetString
      if (time.size == 0) NullTime
      else  OpaqueTimeFormat(time)
    }

    def getRegisteredDelivery = RegisteredDelivery(bi.getByte)

    def getTlv = {
      val tag = Tag.getOrInvalid(bi.getShort)
      val len = bi.getShort & 0xffff
      val value = bi.getOctetString(len)
      Tlv(tag, value)
    }

    def getTlvs: List[Tlv] = {
      if (bi.isEmpty) Nil
      else bi.getTlv :: getTlvs
    }
  }
}
