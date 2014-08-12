package akkasmpp.userdata

import java.nio.ByteBuffer

import akkasmpp.protocol.DataCodingScheme
import akkasmpp.protocol.DataCodingScheme.DataCodingScheme
import akkasmpp.userdata.Charset.CodePoint
import com.cloudhopper.commons.charset.{GSMCharset, ISO88591Charset, UCS2Charset}

import scala.annotation.tailrec

trait CharacterEncodingStrategy {

  def chooseEncoding(msg: String): Charset

  def stringToSegments(msg: String): List[Array[Byte]] = stringToSegments(msg, chooseEncoding(msg))

  def stringToSegments(msg: String, charset: Charset, udhSize: Int = 0): List[Array[Byte]] = {
    takeCharUnits(msg, charset, charset.maxCharUnitsInSegment(udhSize)) match {
      case (encoded, None) => List(encoded)
      case (encoded, Some(remaining)) => encoded :: stringToSegments(remaining, charset, udhSize)
    }
  }

  def takeCharUnits(msg: String, encoding: Charset, nCharUnits: Int): (Array[Byte], Option[String]) = {
    @tailrec
    def findSplit(index: Int, totalUnits: Int): Int = {
      if (index >= msg.length) {
        msg.length // we've gone too far.
      } else {
        val cp = msg.codePointAt(index)
        val codeUnits = encoding.charUnits(cp)
        if (totalUnits + codeUnits > nCharUnits) {
          // this means we can't add the next character, it puts us over nCharUnits
          index
        } else {
          findSplit(index + Character.charCount(cp), totalUnits + codeUnits)
        }
      }
    }
    val split = findSplit(0, 0)
    val (segment, extra) = msg splitAt split
    (encoding.encode(segment), if (extra.isEmpty) None else Some(extra))
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
