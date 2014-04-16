package akkasmpp.userdata

import akkasmpp.userdata.InformationElementIdentifier


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

case class UserDataHeader(elements: Seq[InformationElement]) {
  val dataLength = elements.map(_.dataLength + 2).sum
}

case class InformationElement(identifier: InformationElementIdentifier.type, data: Array[Byte]) {
  val dataLength = data.length
}
