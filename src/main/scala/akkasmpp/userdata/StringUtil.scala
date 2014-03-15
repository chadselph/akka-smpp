package akkasmpp.userdata

/**
 * Implicit string conversions
 */
object StringUtil {
  /*
     * Java's string object is bullshit. I had to fix it.
     * Inspired by: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5003547
     */
  object Implicits {
    implicit class StringWithCodePointIterator(val s: String) {
      def codePoints = {
        new Iterable[Int] {
          var offset = 0
          def iterator = new Iterator[Int] {
            def next() = {
              val c = s.codePointAt(offset)
              offset += Character.charCount(c)
              c
            }
            def hasNext = offset < s.length
          }
        }
      }
      def codeUnits = {
        new Iterable[Char] {
          var offset = 0
          def iterator = new Iterator[Char] {
            def next() = {
              val c = s.charAt(offset)
              offset += 1
              c
            }
            def hasNext = offset < s.length
          }
        }
      }
    }
  }
}
