package akkasmpp.userdata

import com.cloudhopper.commons.charset.{ISO88591Charset, GSMCharset, UCS2Charset}
import scala.annotation.tailrec
import akkasmpp.protocol.DataCodingScheme
import akkasmpp.protocol.DataCodingScheme.DataCodingScheme
import akkasmpp.userdata.Charset.CodePoint
import java.nio.ByteBuffer

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
    val folds = countFoldsWhile(0)(codepoints)(_ + charset.charUnits(_))(_ <= charset.maxCharUnitsInSegment(0))
    if (folds == codepoints.length) List(charset.encode(msg))
    else {
      val (piece, rest) = msg.splitAt(folds)
      charset.encode(piece) :: stringToSegments(rest, charset)
    }
  }

  def segmentsToString(segments: Seq[Array[Byte]], charset: Charset) = segments.map(charset.decode).mkString

}

object Charset {
  type CodePoint = Int
  type DCSMapping = Map[DataCodingScheme, Charset]

  val DefaultEncodingMap: DCSMapping = Map(
    DataCodingScheme.SmscDefaultAlphabet -> DefaultGsmCharset,
    DataCodingScheme.IA5 -> DefaultIA5Charset,
    DataCodingScheme.Latin1 -> DefaultLatin1Charset,
    DataCodingScheme.UCS2 -> DefaultUCS2Charset
  )

}

trait Charset {

  import Charset.{DCSMapping, CodePoint}

  def encode(s: CharSequence): Array[Byte]
  def decode(ba: Array[Byte]): String
  def charUnits(c: CodePoint): Int
  def bitsPerCodeUnit: Int
  def maxCharUnitsInSegment(udhLength: Int = 0) = (140 - udhLength) * 8 / bitsPerCodeUnit
}

object DefaultGsmCharset extends GSMCharset with Charset {
  // convert from chars to Int because some codepoints are actually 2 Chars
  private val CharSet = GSMCharset.CHAR_TABLE.toSet.map((_: Char).toInt)
  private val ExtCharSet = GSMCharset.EXT_CHAR_TABLE.toSet.filter(_ != 0).map(_.toInt)
  def charUnits(c: CodePoint): Int =
    if (CharSet.contains(c)) 1
    else if (ExtCharSet.contains(c)) 2
    else 1 // size of replacement character

  def bitsPerCodeUnit = 7
}

object DefaultUCS2Charset extends UCS2Charset with Charset {
  def charUnits(c: CodePoint) = Character.charCount(c)
  def bitsPerCodeUnit = 16
}

object DefaultIA5Charset extends Charset {
  val ascii = java.nio.charset.Charset.forName("ASCII")
  override def encode(s: CharSequence): Array[Byte] = s.toString.getBytes(ascii)
  // actually not sure if 7 would work here. depends on carrier, I think
  override def bitsPerCodeUnit: Int = 8
  override def decode(ba: Array[Byte]): String = ascii.decode(ByteBuffer.wrap(ba)).toString
  override def charUnits(c: CodePoint): Int = 1
}

object DefaultLatin1Charset extends ISO88591Charset with Charset {
  override def bitsPerCodeUnit = 8
  override def charUnits(c: CodePoint): Int = 1
}

object LosslessEncodingStrategy extends CharacterEncodingStrategy {
  /**
   * Choose either GSM or UCS2 as for most carriers these are all that is supported.
   */
  def chooseEncoding(msg: String) = {
    if (GSMCharset.canRepresent(msg)) DefaultGsmCharset
    else DefaultUCS2Charset
  }
}

object ConcatLosslessEncodingStrategy extends CharacterEncodingStrategy {
  def chooseEncoding(msg: String): Charset = {
    LosslessEncodingStrategy.chooseEncoding(msg)
  }
}
