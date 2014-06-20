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
