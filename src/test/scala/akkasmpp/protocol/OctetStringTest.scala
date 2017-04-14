package akkasmpp.protocol

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FunSpec, Matchers}

class OctetStringTest extends FunSpec with Matchers with GeneratorDrivenPropertyChecks {

  describe("OctetString") {
    it ("should pattern match empty") {
      OctetString.empty match {
        case OctetString() =>
        case _ => fail("didn't match empty octet string")
      }
    }
    it ("should pattern match when constructed with an Array[Byte]") {
      OctetString.fromBytes(Array[Byte](1,2,3,4)) match {
        case OctetString(100, 8) => fail("matching broken")
        case OctetString(a, b, c @ 3, d) =>
          a should be (1)
          b should be (2)
          c should be (3)
          d should be (4)
        case _ => fail("matching broken")
      }
    }

    it ("should always get back to what we started when converting to array and back") {
      val arrayGen = Gen.containerOf[Array, Byte](Arbitrary.arbitrary[Byte])
      forAll(arrayGen) { byteArr =>
        byteArr should be (OctetString.fromBytes(byteArr).data.toArray)
      }
    }
  }
}

