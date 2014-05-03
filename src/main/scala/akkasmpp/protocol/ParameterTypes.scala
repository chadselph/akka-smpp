

package akkasmpp.protocol

import akkasmpp.protocol.RegisteredDelivery.SmscDelivery.SmscDelivery
import akkasmpp.protocol.RegisteredDelivery.SmeAcknowledgement.SmeAcknowledgement
import akkasmpp.protocol.RegisteredDelivery.IntermediateNotification.IntermediateNotification
import java.util.{NoSuchElementException, Arrays}

object SmppTypes {
  type Integer = Int // An unsigned value with the defined number of octets.
  type SequenceNumber = Integer
  type MessageId = COctetString

  /**
   * The regular Enumeration#ValueSet.toBitMask doesn't really
   * do what I want. I want it to use the enum's actual values,
   * not 2**(index of enum). This is so I can have enums like
   *
   *    val SomeProtocolDefinedThing = Value(64)
   *    val SomeOtherThing = Value(128)
   *
   */
  implicit class RichEnumValueSet(val vs: Enumeration#ValueSet) {

    def getBits = vs.foldRight(0)(_.id | _)

    def fromBits(bits: Long) = {
    }
  }
}

/**
 * Immutable wrapper for  array of octets followed by a 0
 */
object COctetString {
  def empty = new COctetString(Array[Byte]())
}

class COctetString(protected val data: Array[Byte]) {

  def this(s: String)(implicit charEncoding: java.nio.charset.Charset) = this(s.getBytes(charEncoding))

  override def equals(other: Any) = other match {
      case o: COctetString => Arrays.equals(data, o.data)
      case _ => false
    }

  def copyTo(dest: Array[Byte]) = data.copyToArray(dest)

  def size = data.size

  def asString(implicit charEncoding: java.nio.charset.Charset) = {
    new String(data)
  }

  override def toString = new String(data, java.nio.charset.Charset.forName("ASCII"))
}

/**
 * String of octets with no null terminator
 * @param data
 */
class OctetString(protected val data: Array[Byte]) {

  def this(b: Byte) = this(Array(b))
  def size = data.size
  def copyTo(dest: Array[Byte]) = data.copyToArray(dest)
  override def equals(other: Any) = other match {
    case o: OctetString => Arrays.equals(data, o.data)
    case _ => false
  }
  override def toString = {
    data.map("%02X".format(_)).mkString("<OctetString: ", "", ">")
  }
}

object CommandId extends Enumeration {
  type CommandId = Value
  val generic_nack = Value(0x80000000)
  val bind_receiver = Value(0x00000001)
  val bind_receiver_resp = Value(0x80000001)
  val bind_transmitter = Value(0x00000002)
  val bind_transmitter_resp = Value(0x80000002)
  val query_sm = Value(0x00000003)
  val query_sm_resp = Value(0x80000003)
  val submit_sm = Value(0x00000004)
  val submit_sm_resp = Value(0x80000004)
  val deliver_sm = Value(0x00000005)
  val deliver_sm_resp = Value(0x80000005)
  val unbind = Value(0x00000006)
  val unbind_resp = Value(0x80000006)
  val replace_sm = Value(0x00000007)
  val replace_sm_resp = Value(0x80000007)
  val cancel_sm = Value(0x00000008)
  val cancel_sm_resp = Value(0x80000008)
  val bind_transceiver = Value(0x00000009)
  val bind_transceiver_resp = Value(0x80000009)
  // Reserved 0x0000000A 0x8000000A
  val outbind = Value(0x0000000B)
  // Reserved 0x0000000C - 0x00000014 0x8000000B - 0x80000014
  val enquire_link = Value(0x00000015)
  val enquire_link_resp = Value(0x80000015)
  // Reserved 0x00000016 - 0x00000020 0x80000016 - 0x80000020
  val submit_multi = Value(0x00000021)
  val submit_multi_resp = Value(0x80000021)
  val alert_notification = Value(0x00000102)
  val data_sm = Value(0x00000103)
  val data_sm_resp = Value(0x80000103)

  def getClass(v: Value): Class[_ <: Pdu] = {
    // would like to make this a little more... dynamic.
    v match {
      case this.generic_nack => classOf[GenericNack]
      case this.bind_receiver => classOf[BindReceiver]
      case this.bind_receiver_resp => classOf[BindReceiverResp]
      case this.bind_transmitter => classOf[BindTransmitter]
      case this.bind_transmitter_resp => classOf[BindTransmitterResp]
      case this.query_sm => classOf[QuerySm]
      case this.query_sm_resp => classOf[QuerySmResp]
      case this.submit_sm => classOf[SubmitSm]
      case this.submit_sm_resp => classOf[SubmitSmResp]
      case this.deliver_sm => classOf[DeliverSm]
      case this.deliver_sm_resp => classOf[DeliverSmResp]
      case this.unbind => classOf[Unbind]
      case this.unbind_resp => classOf[UnbindResp]
      case this.replace_sm => classOf[ReplaceSm]
      case this.replace_sm_resp => classOf[ReplaceSmResp]
      case this.cancel_sm => classOf[CancelSm]
      case this.cancel_sm_resp => classOf[CancelSmResp]
      case this.bind_transceiver => classOf[BindTransceiver]
      case this.bind_transceiver_resp => classOf[BindTransceiverResp]
      case this.outbind => classOf[Outbind]
      case this.enquire_link => classOf[EnquireLink]
      case this.enquire_link_resp => classOf[EnquireLinkResp]
      case this.submit_multi => classOf[SubmitMulti]
      case this.submit_multi_resp => classOf[SubmitMultiResp]
      case this.alert_notification => classOf[AlertNotification]
      case this.data_sm => classOf[DataSm]
      case this.data_sm_resp => classOf[DataSmResp]
    }
  }

}

object TypeOfNumber extends Enumeration {
  type TypeOfNumber = Value
  val Unknown = Value(0x00)
  val International = Value(0x01)
  val National = Value(0x02)
  val NationalSpecific = Value(0x03)
  val SubscriberNumber = Value(0x04)
  val AlphaNumeric = Value(0x05)
  val Abbreviated = Value(0x06)
}

object NumericPlanIndicator extends Enumeration {
  type NumericPlanIndicator = Value
  val Unknown = Value(0x00)
  val E164 = Value(0x01)
  val Data = Value(0x03)
  val Telex = Value(0x04)
  val LandMobile = Value(0x06)
  val National = Value(0x08)
  val Private = Value(0x09)
  val ERMES = Value(0x10)
  val Internet = Value(0x14)
  val WapClientId = Value(0x18)
}

object CommandStatus extends Enumeration {

  type CommandStatus = Value
  val ESME_ROK  = Value(0x00000000) //  No Error
  val ESME_RINVMSGLEN = Value(0x00000001) //  Message Length is invalid
  val ESME_RINVCMDLEN = Value(0x00000002) //  Command Length is invalid
  val ESME_RINVCMDID = Value(0x00000003) //  Invalid Command ID
  val ESME_RINVBNDSTS = Value(0x00000004) //  Incorrect BIND Status for given com- mand
  val ESME_RALYBND = Value(0x00000005) //  ESME Already in Bound State
  val ESME_RINVPRTFLG = Value(0x00000006) //  Invalid Priority Flag
  val ESME_RINVREGDLVFLG = Value(0x00000007) //  Invalid Registered Delivery Flag
  val ESME_RSYSERR = Value(0x00000008) //  System Error
  // Reserved 0x00000009
  val ESME_RINVSRCADR = Value(0x0000000A) //  Invalid Source Address
  val ESME_RINVDSTADR = Value(0x0000000B) //  Invalid Dest Addr
  val ESME_RINVMSGID = Value(0x0000000C) //  Message ID is invalid
  val ESME_RBINDFAIL = Value(0x0000000D) //  Bind Failed
  val ESME_RINVPASWD = Value(0x0000000E) //  Invalid Password
  val ESME_RINVSYSID = Value(0x0000000F) //  Invalid System ID
  // Reserved 0x00000010
  val ESME_RCANCELFAIL = Value(0x00000011) //  Cancel SM Failed
  // 0x00000012 Reserved
  val ESME_RREPLACEFAIL = Value(0x00000013) //  Replace SM Failed
  val ESME_RMSGQFUL = Value(0x00000014) //  Message Queue Full
  val ESME_RINVSERTYP = Value(0x00000015) //  Invalid Service Type
  // 0x0000016- 0x00000032 Reserved
  val ESME_RINVNUMDESTS = Value(0x00000033) //  Invalid number of destinations
  val ESME_RINVDLNAME = Value(0x00000034) // Invalid Distribution List name
  // 0x00000035- 0x0000003F Reserved
  val ESME_RINVDESTFLAG = Value(0x00000040) // Destination flag is invalid (submit_multi)
  // Reserved 0x00000041
  val ESME_RINVSUBREP = Value(0x00000042) // Invalid ‘submit with replace’ request (i.e. submit_sm with replace_if_present_flag set)
  val ESME_RINVESMCLASS = Value(0x00000043) // Invalid esm_class field data
  val ESME_RCNTSUBDL = Value(0x00000044) // Cannot Submit to Distribution List
  val ESME_RSUBMITFAIL = Value(0x00000045) // submit_sm or submit_multi failed
  // 0x00000046 - 0x00000047 Reserved
  val ESME_RINVSRCTON = Value(0x00000048) // Invalid Source address TON
  val ESME_RINVSRCNPI = Value(0x00000049) // Invalid Source address NPI
  val ESME_RINVDSTTON = Value(0x00000050) // Invalid Destination address TON
  val ESME_RINVDSTNPI = Value(0x00000051) // Invalid Destination address NPI
  // Reserved 0x00000052
  val ESME_RINVSYSTYP = Value(0x00000053) // Invalid system_type field
  val ESME_RINVREPFLAG = Value(0x00000054) // Invalid replace_if_present flag
  val ESME_RINVNUMMSGS = Value(0x00000055) // Invalid number of messages
  // 0x000056- 0x00000057 Reserved
  val ESME_RTHROTTLED = Value(0x00000058) // Throttling error (ESME has exceeded allowed message limits)
  // Reserved 0x00000059- 0x00000060
  val ESME_RINVSCHED = Value(0x00000061) // Invalid Scheduled Delivery Time
  val ESME_RINVEXPIRY = Value(0x00000062) // Invalid message validity period (Expiry time)
  val ESME_RINVDFTMSGID = Value(0x00000063) // Predefined Message Invalid or Not Found
  val ESME_RX_T_APPN = Value(0x00000064) // ESME Receiver Temporary App Error Code
  val ESME_RX_P_APPN = Value(0x00000065) // ESME Receiver Permanent App Error Code
  val ESME_RX_R_APPN = Value(0x00000066) // ESME Receiver Reject Message Error Code
  val ESME_RQUERYFAIL = Value(0x00000067) // query_sm request failed
  // Reserved 0x00000068 - 0x000000BF Reserved
  val ESME_RINVOPTPARSTREAM = Value(0x000000C0) // Error in the optional part of the PDU Body.
  val ESME_ROPTPARNOTALLWD = Value(0x000000C1) // Optional Parameter not allowed
  val ESME_RINVPARLEN = Value(0x000000C2) // Invalid Parameter Length.
  val ESME_RMISSINGOPTPARAM = Value(0x000000C3) // Expected Optional Parameter missing
  val ESME_RINVOPTPARAMVAL = Value(0x000000C4) // Invalid Optional Parameter Value
  val Reserved = Value(0x000000C5) // - 0x000000FD Reserved
  val ESME_RDELIVERYFAILURE = Value(0x000000FE) // Delivery Failure (used for data_sm_resp)
  val ESME_RUNKNOWNERR = Value(0x000000FF) // Unknown Error
  /*
    Reserved for SMPP extension 0x00000100- 0x000003FF Reserved for SMPP extension
    Reserved for SMSC vendor specific errors 0x00000400- 0x000004FF Reserved for SMSC vendor specific errors
  */
  for (i <- 0x400 to 0x4ff) {
    Value(i, s"SMSC Vendor Specific Error: $i")
  }

  /*
    Reserved 0x00000500- 0xFFFFFFFF Reserved
  */
}

object ServiceType {
  type ServiceType = COctetString
  def value(s: String) = new COctetString(s)(java.nio.charset.Charset.forName("ASCII"))

  val Default = value("")
  val CellularMessaging = value("CMT")
  val CellularPaging = value("CPT")
  val VoiceMailNotification = value("VMN")
  val VoiceMailAlerting = value("VMA")
  val WirelessApplicationProtocol = value("WAP")
  val UnstructuredSupplementaryServicesData = value("USSD")
}

abstract class FlexibleEnumeration extends Enumeration {

  class InvalidValue(val id: Int) extends Value {
    override def toString = f"Invalid (0x$id%x)"
  }

  def getOrInvalid(id: Int): Value = {
    try {
      super.apply(id)
    } catch {
      case _: NoSuchElementException =>
        new InvalidValue(id)
    }
  }

  def getOrInvalid(id: Byte): Value = getOrInvalid(id.toInt & 0xff)
}

object Priority extends FlexibleEnumeration {
  type Priority = Value

  val Level0 = Value(0)
  val Level1 = Value(1)
  val Level2 = Value(2)
  val Level3 = Value(3)
}

object EsmClass {

  object MessagingMode extends Enumeration {
    type MessagingMode = Value
    val Default = Value(0)
    val DataGram = Value(1)
    val Forward = Value(2)
    val StoreAndForward = Value(3)
  }
  object MessageType extends Enumeration {
    type MessageType = Value
    val NormalMessage = Value(0)
    val SmscDeliveryReceipt = Value(4)
    val DeliveryAcknowledgement = Value(8)
    val ManualUserAcknowledgement = Value(16)
    val ConversationAbort = Value(24)
    val IntermediateDeliveryNotitication = Value(32)
  }
  object Features extends Enumeration {
    /*
    Using bit numbers for these enums instead of actual values
    so the bitset will work predictably.
     */
    type Features = ValueSet
    val UDHIIndicator = Value(6) // 2^6 = 64
    val SetReplyPath = Value(7)  // 2^7 = 128
  }
  def apply(messagingMode: EsmClass.MessagingMode.MessagingMode, messageType: EsmClass.MessageType.MessageType, features: EsmClass.Features.Value*): EsmClass = {
    EsmClass(messagingMode, messageType, EsmClass.Features.ValueSet(features:_*))
  }
  def apply(b: Byte): EsmClass = {
    val messagingMode = EsmClass.MessagingMode(b & 3)
    val messagingType = EsmClass.MessageType(b & 60)
    val features = EsmClass.Features.ValueSet.fromBitMask(Array(b & 192L))
    EsmClass(messagingMode, messagingType, features.toSeq: _*)
  }
}

case class EsmClass(messagingMode: EsmClass.MessagingMode.MessagingMode, messageType: EsmClass.MessageType.MessageType, features: EsmClass.Features.ValueSet)

abstract class TimeFormat
case object NullTime extends TimeFormat
case class RelativeTimeFormat(years: Int = 0, months: Int = 0, days: Int = 0, hours: Int = 0, minutes: Int = 0, seconds: Int = 0) extends TimeFormat
case class AbsoluteTimeFormat(year: Int = 0, month: Int = 0, day: Int = 0, hour: Int = 0, minute: Int = 0, second: Int = 0) extends TimeFormat

object RegisteredDelivery {

  object SmscDelivery extends Enumeration {
    type SmscDelivery = Value
    val NoneRequested = Value(0)
    val SuccessAndFailureRequested = Value(1)
    val FailureRequested = Value(2)
  }

  object SmeAcknowledgement extends Enumeration {
    type SmeAcknowledgement = Value
    val NoneRequested = Value(0)
    val DeliveryRequested = Value(4)
    val ManualUserRequested = Value(8)
    val BothRequested = Value(12)
  }

  object IntermediateNotification extends Enumeration {
    type IntermediateNotification = Value
    val NotRequested = Value(0)
    val Requested = Value(16)
  }

  def apply(b: Byte): RegisteredDelivery =
    RegisteredDelivery(SmscDelivery(b & 3), SmeAcknowledgement(b & 12), IntermediateNotification(b & 16))

}
case class RegisteredDelivery(smscDelivery: SmscDelivery = RegisteredDelivery.SmscDelivery.NoneRequested,
                              smeAcknowledgement: SmeAcknowledgement = RegisteredDelivery.SmeAcknowledgement.NoneRequested,
                              intermediateNotification: IntermediateNotification = RegisteredDelivery.IntermediateNotification.NotRequested)

object DataCodingScheme extends Enumeration {
  type DataCodingScheme = Value

  val SmscDefaultAlphabet = Value(0)
  val IA5 = Value(1)  // ASCII, afaik
  val OctetUnspecified = Value(2)
  val Latin1 = Value(3)
  val OctetUnspecified_ = Value(4)
  val JIS = Value(5)
  val Cyrllic = Value(6)
  val LatinHebrew = Value(7)
  val UCS2 = Value(8)
  val PictogramEncoding = Value(9)
  val MusicCodes = Value(10)
  val ExtendedKanji = Value(13)
  val KsC5601 = Value(14)

  /* XXX: MWI not supported */

}

object MessageState extends Enumeration {
  type MessageState = Value

  val ENROUTE = Value(1)        // The message is in enroute state.
  val DELIVERED = Value(2)      // Message is delivered to destination
  val EXPIRED = Value(3)        // Message validity period has expired.
  val DELETED = Value(4)        // Message has been deleted.
  val UNDELIVERABLE = Value(5)  // Message is undeliverable
  val ACCEPTED = Value(6)       // Message is in accepted state (i.e. has been manually read on behalf of the subscriber by customer service)
  val UNKNOWN = Value(7)        // Message is in invalid state
  val REJECTED = Value(8)       // Message is in a rejected state
}

case class Tlv(tag: Tag.Tag, value: OctetString)

object Tag extends FlexibleEnumeration {

  type Tag = Value

  val dest_addr_subunit	= Value(0x0005)
  val dest_network_type	= Value(0x0006)
  val dest_bearer_type = Value(0x0007)
  val dest_telematics_id = Value(0x0008)
  val source_addr_subunit = Value(0x000D)
  val source_network_type = Value(0x000E)
  val source_bearer_type = Value(0x000F)
  val source_telematics_id = Value(0x0010)
  val qos_time_to_live = Value(0x0017)
  val payload_type = Value(0x0019)
  val additional_status_info_text = Value(0x001D)
  val receipted_message_id = Value(0x001E)
  val ms_msg_wait_facilities = Value(0x0030)
  val privacy_indicator = Value(0x0201)
  val source_subaddress = Value(0x0202)
  val dest_subaddress = Value(0x0203)
  val user_message_reference = Value(0x0204)
  val user_response_code = Value(0x0205)
  val source_port = Value(0x020A)
  val destination_port = Value(0x020B)
  val sar_msg_ref_num = Value(0x020C)
  val language_indicator = Value(0x020D)
  val sar_total_segments = Value(0x020E)
  val sar_segment_seqnum = Value(0x020F)
  val SC_interface_version = Value(0x0210)
  val callback_num_pres_ind = Value(0x0302)
  val callback_num_atag = Value(0x0303)
  val number_of_messages = Value(0x0304)
  val callback_num = Value(0x0381)
  val dpf_result = Value(0x0420)
  val set_dpf = Value(0x0421)
  val ms_availability_status = Value(0x0422)
  val network_error_code = Value(0x0423)
  val message_payload = Value(0x0424)
  val delivery_failure_reason = Value(0x0425)
  val more_messages_to_send = Value(0x0426)
  val message_state = Value(0x0427)
  val ussd_service_op = Value(0x0501)
  val display_time = Value(0x1201)
  val sms_signal = Value(0x1203)
  val ms_validity = Value(0x1204)
  val alert_on_message_delivery = Value(0x130C)
  val its_reply_type = Value(0x1380)
  val its_session_info = Value(0x1383)

}


