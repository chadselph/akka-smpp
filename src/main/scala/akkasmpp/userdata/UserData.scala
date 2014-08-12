package akkasmpp.userdata

import akkasmpp.protocol.OctetString

/**
 * Utility functions that deal with the user data field (short_message) as a whole.
 */
object UserData {

  def headerAndPayload(userData: OctetString, udhi: Boolean): (Option[UserDataHeader], Array[Byte]) = {
    if (udhi) {
      val header = UserDataHeader.fromShortMessage(userData)
      val headerLength = header.dataLength + 1 // +1 for the header length field itself
      (Some(header), userData.data drop headerLength)
    } else {
      (None, userData.data)
    }
  }
}

case class UserData(header: Option[UserDataHeader], data: Array[Byte]) {
  def toOctetString: OctetString = {
    val d = header match {
      case None => data
      case Some(h) =>
        val dest = new Array[Byte](h.dataLength + 1 + data.length)
        dest(0) = (h.dataLength & 255).toByte
        var i = 1
        h.elements.foreach { elem =>
          dest(i) = (elem.identifier.id & 255).toByte
          dest(i+1) = elem.dataLength
          Array.copy(elem.data, 0, dest, i + 2, elem.dataLength)
          i += elem.dataLength + 2
        }
        Array.copy(data, 0, dest, i, data.length)
        dest
    }
    new OctetString(d)
  }
}
