package akkasmpp.userdata

import org.scalatest.{Matchers, FunSuite}
import akkasmpp.protocol.OctetString

class UserDataHeaderTest extends FunSuite with Matchers {

  test("parsing a valid UDH header") {
    val udh = UserDataHeader.fromShortMessage(OctetString(5, 0, 3, 9, 1, 2, 0, 0, 0, 0))
    udh.dataLength should be (5)
    val element = udh.elements(0)
    element.dataLength should be (3)
    element.data should be (Array[Byte](9, 1, 2))
  }
}
