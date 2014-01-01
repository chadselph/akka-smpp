/**
 *
 * Created by chad on 12/29/13.
 */

import akkasmpp.protocol.EsmClass
import akkasmpp.protocol.EsmClass.{MessageType, MessagingMode, Features}

//EsmClass(MessagingMode(1), MessageType(0), Features.ValueSet.fromBitMask(Array(192)).toSeq: _*)
object Ee extends Enumeration {
  type Ee = Value
  val Aa = Value(1)
  val Ab = Value(2)
  val Ac = Value(3)
}
(Ee.Aa + Ee.Ac).toBitMask
//(Features.SetReplyPath + Features.UDHIIndicator).toBitMask


object Features extends Enumeration {
  type Features = Value
  val Defaut = Value(0)
  val UDHIIndicator = Value(6) // 64
  val SetReplyPath = Value(7) // 128
}

(Features.SetReplyPath + Features.UDHIIndicator).toBitMask
Features.ValueSet.fromBitMask(Array(192L))
Features.ValueSet.fromBitMask(Array(0L))
