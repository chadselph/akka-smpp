package akkasmpp.userdata

import org.scalatest.{Matchers, FunSuite}

class CharacterEncodingStrategyTest extends FunSuite with Matchers {

  test("splitting up messages based on encoding works") {
    LosslessEncodingStrategy.chooseEncoding("ÿ not gsm").maxCharUnitsInSegment should equal (70)
    LosslessEncodingStrategy.chooseEncoding("only gsm characters").maxCharUnitsInSegment should equal (160)

    // with space for extra UDH headers

    val segments = LosslessEncodingStrategy.stringToSegments("a" * 161)
    segments(0).length should be (160)
    segments(1).length should be (1)
  }

}
