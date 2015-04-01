package akkasmpp.userdata

import akka.util.ByteStringBuilder
import akkasmpp.protocol.OctetString

/**
 * Utility functions that deal with the user data field (short_message) as a whole.
 */
object UserData {

  def fromOctetString(userData: OctetString, udhi: Boolean): UserData = {
    if (udhi) {
      val header = UserDataHeader.fromShortMessage(userData)
      val headerLength = header.dataLength + 1 // +1 for the header length field itself
      UserData(Some(header), new OctetString(userData.data drop headerLength))
    } else {
      UserData(None, new OctetString(userData.data))
    }
  }
}

case class UserData(header: Option[UserDataHeader], payload: OctetString) {
  def toOctetString: OctetString = {
    header match {
      case None => payload
      case Some(h) =>
        val dest = new ByteStringBuilder
        dest.sizeHint(h.dataLength & 255 + 1 + payload.size)
        dest.putByte(h.dataLength)
        h.elements.foreach { elem =>
          dest.putByte(elem.identifier.id.toByte)
          dest.putByte(elem.dataLength)
          dest.append(elem.data.data)
        }
        dest.append(payload.data)
        new OctetString(dest.result())
    }
  }
}
