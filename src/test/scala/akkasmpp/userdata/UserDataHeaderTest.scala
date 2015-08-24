package akkasmpp.userdata

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, FunSuite}
import akkasmpp.protocol.OctetString

@RunWith(classOf[JUnitRunner])
class UserDataHeaderTest extends FunSuite with Matchers {

  test("parsing a valid UDH header") {
    val udh = UserDataHeader.fromShortMessage(OctetString(5, 0, 3, 9, 1, 2, 0, 0, 0, 0))
    udh.dataLength should be (5)
    val element = udh.elements(0)
    element.dataLength should be (3)
    element.data should be (OctetString(9, 1, 2))
  }
}
