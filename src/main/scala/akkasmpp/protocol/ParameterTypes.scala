package akkasmpp.protocol

object SmppTypes {
  type OctetString = Array[Byte] // close enough...
  type COctetString = Array[Byte] // null terminated string
  type Integer = Int // An unsigned value with the defined number of octets.
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
      case this.bind_receiver => ???
      case this.bind_receiver_resp => ???
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
      case this.bind_transceiver => ???
      case this.bind_transceiver_resp => ???
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
    Value(i, "SMSC Vendor Specific Error")
  }

  /*
    Reserved 0x00000500- 0xFFFFFFFF Reserved
  */
}

object ServiceType extends Enumeration {
  type ServiceType = Value
  val Default = Value("")
  val CellularMessaging = Value("CMT")
  val CellularPaging = Value("CPT")
  val VoiceMailNotification = Value("VMN")
  val VoiceMailAlerting = Value("VMA")
  val WirelessApplicationProtocol = Value("WAP")
  val UnstructuredSupplementaryServicesData = Value("USSD")
}

object Priority extends Enumeration {
  type Priority = Value

  val Level0 = Value(0)
  val Level1 = Value(1)
  val Level2 = Value(2)
  val Level3 = Value(3)
}

object EsmClass extends Enumeration {
  type EsmClass = Value

  object MessagingMode {
    val Default = Value(0)
    val DataGram = Value(1)
    val Forward = Value(2)
    val StoreAndForward = Value(3)
  }
  object MessageType {
    //val Default = Value(0)

  }
  val UDHIIndicator = Value(64)
  val SetReplyPath = Value(128)
}

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

  import SmscDelivery.SmscDelivery
  import SmeAcknowledgement.SmeAcknowledgement
  import IntermediateNotification.IntermediateNotification

  case class RegisteredDelivery(smscDelivery: SmscDelivery = SmscDelivery.NoneRequested,
                                smeAcknowledgement: SmeAcknowledgement = SmeAcknowledgement.NoneRequested,
                                intermediateNotification: IntermediateNotification = IntermediateNotification.NotRequested)
}

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

case class Tlv(tag: Short, length: Short, value: Array[Byte])

