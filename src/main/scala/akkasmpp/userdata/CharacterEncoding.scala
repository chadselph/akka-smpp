package akkasmpp.userdata

import com.cloudhopper.commons.charset.{GSMCharset, UCS2Charset}
import scala.annotation.tailrec

trait CharacterEncodingStrategy {

  import StringUtil.Implicits._

  def chooseEncoding(msg: String): Charset

  def stringToSegments(msg: String): List[Array[Byte]] = stringToSegments(msg, chooseEncoding(msg))

  def stringToSegments(msg: String, charset: Charset): List[Array[Byte]] = {

    def countFoldsWhile[A, B](initial: A)(seq: Traversable[B])(op: (A, B) => A)(pred: A => Boolean) = {
      @tailrec
      def rec(times: Int, runningVal: A, seq: Traversable[B], op: (A,B) => A, pred: A => Boolean): Int = {
        if (seq.isEmpty) times
        else {
          val nextVal = op(runningVal, seq.head)
          if (!pred(nextVal)) times
          else rec(times + 1, nextVal, seq.tail, op, pred)
        }
      }
      rec(0, initial, seq, op, pred)
    }

    val codepoints = msg.codePoints.toList
    val folds = countFoldsWhile(0)(codepoints)(_ + charset.charUnits(_))(_ <= charset.maxCharUnitsInSegment)
    if (folds == codepoints.length) List(charset.encode(msg))
    else {
      val (piece, rest) = msg.splitAt(folds)
      charset.encode(piece) :: stringToSegments(rest, charset)
    }
  }

  def segmentsToString(segments: Seq[Array[Byte]], charset: Charset) = segments.map(charset.decode).mkString

}

trait Charset {

  type CodePoint = Int

  def encode(s: CharSequence): Array[Byte]
  def decode(ba: Array[Byte]): String
  def charUnits(c: CodePoint): Int
  def bitsPerCodeUnit: Int
  def udhLength: Int
  def maxCharUnitsInSegment = (140 - udhLength) * 8 / bitsPerCodeUnit
}

class DefaultGsmCharset(val udhLength: Int) extends GSMCharset with Charset {
  // convert from chars to Int because some codepoints are actually 2 Chars
  private val CharSet = GSMCharset.CHAR_TABLE.toSet.map((_: Char).toInt)
  private val ExtCharSet = GSMCharset.EXT_CHAR_TABLE.toSet.filter(_ != 0).map(_.toInt)
  def charUnits(c: CodePoint): Int =
    if (CharSet.contains(c)) 1
    else if (ExtCharSet.contains(c)) 2
    else 1 // size of replacement character

  def bitsPerCodeUnit = 7
}

class DefaultUCS2Charset(val udhLength: Int) extends UCS2Charset with Charset {
  def charUnits(c: CodePoint) = Character.charCount(c)
  def bitsPerCodeUnit = 16
}

object LosslessEncodingStrategy extends CharacterEncodingStrategy {
  /**
   * Choose either GSM or UCS2 as for most carriers these are all that is supported.
   */
  def chooseEncoding(msg: String) = {
    if (GSMCharset.canRepresent(msg)) new DefaultGsmCharset(0)
    else new DefaultUCS2Charset(0)
  }
}

object ConcatLosslessEncodingStrategy extends CharacterEncodingStrategy {
  def chooseEncoding(msg: String): Charset = {
    LosslessEncodingStrategy.chooseEncoding(msg)
  }
}
