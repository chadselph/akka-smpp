import akka.util.{ByteIterator, ByteStringBuilder, ByteString}

trait ByteStringHelpers {
  def byteStringFromByte(b: Byte) = ByteString.fromArray(Array(b))
  def parsingTheByte(b: Byte) = byteStringFromByte(b).iterator

  def withByteString(action: (ByteStringBuilder) => ByteStringBuilder) = {
    val bsb = new ByteStringBuilder
    action(bsb)
    new {
      def andThenCheck(check: ByteIterator => Unit) = check(bsb.result().iterator)
    }
  }
}
