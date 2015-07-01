package akkasmpp.userdata

import akkasmpp.protocol.OctetString
import akkasmpp.userdata.{InformationElementIdentifier => IEI}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSuite, Matchers}

@RunWith(classOf[JUnitRunner])
class UserDataTest extends FunSuite with Matchers {

  test("writing a user data with UDH to an octet string should work") {
    val ud = UserData(Some(UserDataHeader(Seq(
      InformationElement(IEI.Concat, OctetString(0x0C, 0x02, 0x01)),
      InformationElement(IEI.TextFormatting, OctetString(0x10, 0x11, 0x12, 0x13, 0x014))
    ))), OctetString(1,2,3,4,5,6,7,8,9,0))

    ud.toOctetString.data should equal (Array[Byte](
      12,
      0, 3, 0xc, 2, 1,  // first concat
      0xa, 5, 0x10, 0x11, 0x12, 0x13, 0x14, // text formatting header
      1, 2, 3, 4, 5, 6, 7, 8, 9, 0 // body
    ))
  }

  test("writing a user data without UDH should work") {
    val ud = UserData(None, OctetString(0, 0, 0, 5, 5, 5))
    ud.toOctetString should equal(OctetString(0, 0, 0, 5, 5, 5))
  }

}
