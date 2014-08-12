package akkasmpp.userdata

import org.scalatest.{Matchers, FunSuite}
import akkasmpp.userdata.{InformationElementIdentifier => IEI}

class UserDataTest extends FunSuite with Matchers {

  test("writing a user data with UDH to an octet string should work") {
    val ud = UserData(Some(UserDataHeader(Seq(
      InformationElement(IEI.Concat, Array[Byte](0x0C, 0x02, 0x01)),
      InformationElement(IEI.TextFormatting, Array[Byte](0x10, 0x11, 0x12, 0x13, 0x014))
    ))), Array[Byte](1,2,3,4,5,6,7,8,9,0))

    ud.toOctetString.data should equal (Array[Byte](
      12,
      0, 3, 0xc, 2, 1,  // first concat
      0xa, 5, 0x10, 0x11, 0x12, 0x13, 0x14, // text formatting header
      1, 2, 3, 4, 5, 6, 7, 8, 9, 0 // body
    ))

  }

}
