package akkasmpp.userdata

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, FunSuite}

@RunWith(classOf[JUnitRunner])
class CharacterEncodingStrategyTest extends FunSuite with Matchers {

  test("splitting up messages based on encoding works") {
    LosslessEncodingStrategy.chooseEncoding("ÿ not gsm").maxCharUnitsInSegment() should equal(70)
    LosslessEncodingStrategy.chooseEncoding("only gsm characters").maxCharUnitsInSegment() should equal(160)

    val segments = LosslessEncodingStrategy.stringToSegments("a" * 161)
    segments(0).length should be(160)
    segments(1).length should be(1)

  }
  test("splitting up messages with a UDH size") {
    // with space for extra UDH headers
    val segments2 = LosslessEncodingStrategy.stringToSegments("a" * 1000, DefaultUCS2Charset, 6)
    segments2.length should be (15)
    segments2(0).length should be (140 - 6)
    segments2(14).length should be (62 * 2)
  }

  test("takeCharUnits should take up to `nCharUnits` of the strings in `encoding`'s charunits") {
    val msg = "this is a bunch of text in a message"
    val res0 = LosslessEncodingStrategy.takeCharUnits(msg, DefaultGsmCharset, 20)
    res0._1.length should be (20)
    res0._2 should be (Some(msg drop 20))

    // multi-unit char on the boundary

    val msg2 = "a" * 19 + '~' + "b" * 20
    val res1 = LosslessEncodingStrategy.takeCharUnits(msg2, DefaultGsmCharset, 20)
    res1._1.length should be (19)
    res1._2 should be (Some(msg2 drop 19))

  }

}
