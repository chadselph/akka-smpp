package akkasmpp.userdata

import akkasmpp.protocol.{OctetString, COctetString}

object InformationElementIdentifier extends Enumeration {
  type InformationElementIdentifier = Value
  val Concat = Value(0x0)
  // basically none of these are ever used. I just included the ones
  // that sound interesting anyway.
  val SpecialSmsMessageIndication = Value(0x1)
  val ApplicationPortAddressingScheme8 = Value(0x4)
  val ApplicationPortAddressingScheme16 = Value(0x5)
  val Concat16BitRef = Value(0x8)
  val WirelessControlMessageProtocol = Value(0x9)
  val TextFormatting = Value(0xa)
  val PredefinedSound = Value(0xb)
  val UserDefiendSound = Value(0xc)
  val PredefinedAnimation = Value(0xd)
  val LargeAnimation = Value(0xe)
  val SmallAnimation = Value(0xf)
}

object UserDataHeader {
  def fromShortMessage(bytes: OctetString): UserDataHeader = {
    val len = bytes.data(0) & 255

    def rec(ieStart: Int): List[InformationElement] = {
      if(ieStart >= len - 1) Nil
      else {
        val t = InformationElementIdentifier(bytes.data(ieStart))
        val l = bytes.data(ieStart + 1)
        val v = bytes.data.slice(ieStart + 2, ieStart + 2 + l)
        InformationElement(t, v) :: rec(ieStart + 3 + l)
      }
    }
    UserDataHeader(rec(1))
  }
}

case class UserDataHeader(elements: Seq[InformationElement]) {
  val dataLength: Byte = (elements.map(_.dataLength + 2).sum & 255).toByte
}

case class InformationElement(identifier: InformationElementIdentifier.InformationElementIdentifier, data: Array[Byte]) {
  val dataLength: Byte = (data.length & 255).toByte
}
