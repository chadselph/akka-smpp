package akkasmpp.protocol

/**
 * PDUs from SMPP 3.4
 */
import akka.util.{ByteString, ByteIterator}
import CommandId.CommandId
import CommandStatus.CommandStatus
import akkasmpp.protocol.TypeOfNumber.TypeOfNumber
import akkasmpp.protocol.NumericPlanIndicator.NumericPlanIndicator
import akkasmpp.protocol.ServiceType.ServiceType
import akkasmpp.protocol.Priority.Priority
import akkasmpp.protocol.DataCodingScheme.DataCodingScheme
import akkasmpp.protocol.MessageState.MessageState
import akkasmpp.protocol.bytestrings.{ReadPdu, WritePdu}
import akkasmpp.protocol.bytestrings.WritePdu.DataSmRespWriter
import akkasmpp.protocol.SmppTypes.MessageId


object Pdu {
  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN
  def fromBytes(bytes: ByteIterator): Pdu = {
    ReadPdu.readPdu(bytes)
  }
}

abstract class Pdu(val commandId: CommandId) {

  lazy val commandLength = toByteString.length
  def sequenceNumber: SmppTypes.Integer
  def toByteString: ByteString
  def commandStatus: CommandStatus
}

trait NullCommandStatus {
  def commandStatus = CommandStatus.ESME_ROK
}

sealed trait EsmeRequest extends Pdu

sealed trait EsmeResponse extends Pdu

sealed trait SmscRequest extends Pdu

sealed trait SmscResponse extends Pdu

sealed trait BindLike extends Pdu with NullCommandStatus with WritePdu.BindWriter with EsmeRequest {
  def systemId: COctetString
  def password: COctetString
  def systemType: COctetString
  def interfaceVersion: Byte
  def addrTon: TypeOfNumber
  def addrNpi: NumericPlanIndicator
  def addressRange: COctetString
}

sealed trait BindRespLike extends Pdu with WritePdu.BindRespWriter with SmscResponse {
  def systemId: Option[COctetString]
  def scInterfaceVersion: Option[Tlv]
}

// deliver_sm / submit_sm. But not submit_multi or data_sm
sealed trait SmLike extends Pdu with WritePdu.SmWriter {
  def serviceType: ServiceType
  def sourceAddrTon: TypeOfNumber
  def sourceAddrNpi: NumericPlanIndicator
  def sourceAddr: COctetString
  def destAddrTon: TypeOfNumber
  def destAddrNpi: NumericPlanIndicator
  def destinationAddr: COctetString
  def esmClass: EsmClass
  def protocolId: Byte
  def priorityFlag: Priority
  def scheduleDeliveryTime: TimeFormat
  def validityPeriod: TimeFormat
  def registeredDelivery: RegisteredDelivery
  def replaceIfPresentFlag: Boolean
  def dataCoding: DataCodingScheme
  def smDefaultMsgId: Byte
  def smLength: Byte
  def shortMessage: OctetString
  def tlvs: List[Tlv]
}

sealed trait SmRespLike extends Pdu with WritePdu.SmRespWriter {
  def messageId: Option[COctetString]
}

/**
 * Binds in transmit only mode
 */
case class BindTransmitter(sequenceNumber: SmppTypes.Integer, systemId: COctetString, password: COctetString,
                           systemType: COctetString, interfaceVersion: Byte, addrTon: TypeOfNumber,
                           addrNpi: NumericPlanIndicator, addressRange: COctetString)
  extends Pdu(CommandId.bind_transmitter) with BindLike

case class BindTransmitterResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer,
                               systemId: Option[COctetString], scInterfaceVersion: Option[Tlv])
  extends Pdu(CommandId.bind_transmitter_resp) with BindRespLike

/**
 * Binds in receive only mode
 */
case class BindReceiver(sequenceNumber: SmppTypes.Integer, systemId: COctetString, password: COctetString,
                        systemType: COctetString, interfaceVersion: Byte, addrTon: TypeOfNumber,
                        addrNpi: NumericPlanIndicator, addressRange: COctetString)
  extends Pdu(CommandId.bind_receiver) with BindLike

case class BindReceiverResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer,
                            systemId: Option[COctetString], scInterfaceVersion: Option[Tlv])
  extends Pdu(CommandId.bind_receiver_resp) with BindRespLike

/**
 * Binds in transmit and receiver mode
 */
case class BindTransceiver(sequenceNumber: SmppTypes.Integer, systemId: COctetString, password: COctetString,
                        systemType: COctetString, interfaceVersion: Byte, addrTon: TypeOfNumber,
                        addrNpi: NumericPlanIndicator, addressRange: COctetString)
  extends Pdu(CommandId.bind_transceiver) with BindLike

case class BindTransceiverResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer,
                            systemId: Option[COctetString], scInterfaceVersion: Option[Tlv])
  extends Pdu(CommandId.bind_transceiver_resp) with BindRespLike

/**
 * For SMSC to initiate the bind (instead of ESME). Unlikely to be supported in this library
 */
case class Outbind(sequenceNumber: SmppTypes.Integer, systemId: COctetString, password: COctetString)
  extends Pdu(CommandId.outbind) with NullCommandStatus with WritePdu.OutbindWriter with SmscRequest

/**
  Tears down the connection
 */
case class Unbind(sequenceNumber: SmppTypes.Integer)
  extends Pdu(CommandId.unbind) with NullCommandStatus with WritePdu.HeaderOnlyWriter with SmscRequest with EsmeRequest

case class UnbindResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer)
  extends Pdu(CommandId.unbind_resp) with WritePdu.HeaderOnlyWriter with SmscResponse with EsmeResponse

/**
 * Response to an invalid PDU
 */

case class GenericNack(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer)
  extends Pdu(CommandId.generic_nack) with WritePdu.HeaderOnlyWriter with SmscResponse with EsmeResponse


/**
 * Submits a short message from the ESME to the SMSC
 */
case class SubmitSm(
                     sequenceNumber: SmppTypes.Integer,
                     serviceType: ServiceType,
                     sourceAddrTon: TypeOfNumber,
                     sourceAddrNpi: NumericPlanIndicator,
                     sourceAddr: COctetString,
                     destAddrTon: TypeOfNumber,
                     destAddrNpi: NumericPlanIndicator,
                     destinationAddr: COctetString,
                     esmClass: EsmClass,
                     protocolId: Byte,
                     priorityFlag: Priority,
                     scheduleDeliveryTime: TimeFormat,
                     validityPeriod: TimeFormat,
                     registeredDelivery: RegisteredDelivery,
                     replaceIfPresentFlag: Boolean,
                     dataCoding: DataCodingScheme,
                     smDefaultMsgId: Byte,
                     smLength: Byte,
                     shortMessage: OctetString,
                     tlvs: List[Tlv]
                     ) extends Pdu(CommandId.submit_sm) with NullCommandStatus with SmLike with EsmeRequest

case class SubmitSmResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.SequenceNumber, messageId: Option[MessageId])
  extends Pdu(CommandId.submit_sm_resp) with SmRespLike with SmscResponse

case class SubmitMulti(
                        sequenceNumber: SmppTypes.Integer,
                        serviceType: ServiceType,
                        sourceAddrTon: TypeOfNumber,
                        sourceAddrNpi: NumericPlanIndicator,
                        sourceAddr: COctetString,
                        numberOfDests: SmppTypes.Integer,
                        destAddresses: List[(TypeOfNumber, NumericPlanIndicator, COctetString)],
                        esmClass: EsmClass,
                        protocolId: Byte,
                        priorityFlag: Priority,
                        scheduleDeliveryTime: TimeFormat,
                        validityPeriod: TimeFormat,
                        registeredDelivery: RegisteredDelivery,
                        replaceIfPresentFlag: Boolean,
                        dataCoding: DataCodingScheme,
                        smDefaultMsgId: Byte,
                        smLength: Byte,
                        shortMessage: OctetString,
                        tlvs: List[Tlv]
                        ) extends Pdu(CommandId.submit_multi) with NullCommandStatus with WritePdu.SubmitMultiWriter with EsmeRequest

case class SubmitMultiResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer,
                            messageId: COctetString, noUnsuccess: Byte,
                            unsuccessSmes: List[(TypeOfNumber, NumericPlanIndicator, COctetString, CommandStatus)])
  extends Pdu(CommandId.submit_sm_resp) with WritePdu.SubmitMultiRespWriter with SmscResponse

case class DeliverSm(
                     sequenceNumber: SmppTypes.Integer,
                     serviceType: ServiceType,
                     sourceAddrTon: TypeOfNumber,
                     sourceAddrNpi: NumericPlanIndicator,
                     sourceAddr: COctetString,
                     destAddrTon: TypeOfNumber,
                     destAddrNpi: NumericPlanIndicator,
                     destinationAddr: COctetString,
                     esmClass: EsmClass,
                     protocolId: Byte,
                     priorityFlag: Priority,
                     scheduleDeliveryTime: TimeFormat,
                     validityPeriod: TimeFormat,
                     registeredDelivery: RegisteredDelivery,
                     replaceIfPresentFlag: Boolean,
                     dataCoding: DataCodingScheme,
                     smDefaultMsgId: Byte,
                     smLength: Byte,
                     shortMessage: OctetString,
                     tlvs: List[Tlv]
                     ) extends Pdu(CommandId.deliver_sm) with NullCommandStatus with SmLike with SmscRequest

case class DeliverSmResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer, messageId: Option[COctetString])
  extends Pdu(CommandId.deliver_sm_resp) with SmRespLike with EsmeResponse

case class DataSm(sequenceNumber: SmppTypes.Integer, serviceType: ServiceType,
                  sourceAddrTon: TypeOfNumber, sourceAddrNpi: NumericPlanIndicator, sourceAddr: COctetString,
                  destAddrTon: TypeOfNumber, destAddrNpi: NumericPlanIndicator, destinationAddr: COctetString,
                  esmClass: EsmClass, registeredDelivery: RegisteredDelivery, dataCoding: DataCodingScheme, tlvs: List[Tlv])
  extends Pdu(CommandId.data_sm) with NullCommandStatus with WritePdu.DataSmWriter with EsmeRequest with SmscRequest

case class DataSmResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer, messageId: COctetString,
                      tlvs: List[Tlv]) extends Pdu(CommandId.data_sm_resp) with DataSmRespWriter with EsmeResponse with SmscResponse

case class QuerySm(sequenceNumber: SmppTypes.Integer, messageId: COctetString,
                   sourceAddrTon: TypeOfNumber, sourceAddrNpi: NumericPlanIndicator, sourceAddr: COctetString)
  extends Pdu(CommandId.query_sm) with NullCommandStatus with WritePdu.QuerySmWriter with EsmeRequest

case class QuerySmResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer,
                       messageId: COctetString, finalDate: TimeFormat, messageState: MessageState, errorCode: SmppTypes.Integer)
  extends Pdu(CommandId.query_sm_resp) with WritePdu.QuerySmRespWriter with SmscResponse

case class CancelSm(sequenceNumber: SmppTypes.Integer, serviceType: ServiceType,
                    messageId: COctetString, sourceAddrTon : TypeOfNumber, sourceAddrNpi: NumericPlanIndicator,
                    sourceAddr: COctetString, destAddrTon: TypeOfNumber, destAddrNpi: NumericPlanIndicator,
                    destinationAddr: COctetString)
  extends Pdu(CommandId.cancel_sm) with NullCommandStatus with WritePdu.CancelSmWriter with EsmeRequest

case class CancelSmResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer)
  extends Pdu(CommandId.cancel_sm_resp) with WritePdu.HeaderOnlyWriter with SmscResponse

case class ReplaceSm(sequenceNumber: SmppTypes.Integer, messageId: COctetString,
                     sourceAddrTon: TypeOfNumber, sourceAddrNpi: NumericPlanIndicator, sourceAddr: COctetString,
                     scheduleDeliveryTime: TimeFormat, validityPeriod: TimeFormat, registeredDelivery: RegisteredDelivery,
                     smDefaultMsgId: Byte, smLength: Byte, shortMessage: OctetString)
  extends Pdu(CommandId.replace_sm) with NullCommandStatus with WritePdu.ReplaceSmWriter with EsmeRequest

case class ReplaceSmResp(commandStatus: CommandStatus, sequenceNumber: SmppTypes.Integer)
  extends Pdu(CommandId.replace_sm_resp) with WritePdu.HeaderOnlyWriter with SmscResponse

case class EnquireLink(sequenceNumber: SmppTypes.Integer)
  extends Pdu(CommandId.enquire_link) with NullCommandStatus with WritePdu.HeaderOnlyWriter with SmscRequest with EsmeRequest

case class EnquireLinkResp(sequenceNumber: SmppTypes.Integer)
  extends Pdu(CommandId.enquire_link_resp) with NullCommandStatus with WritePdu.HeaderOnlyWriter with SmscResponse with EsmeResponse

case class AlertNotification(sequenceNumber: SmppTypes.Integer,
                             sourceAddrTon: TypeOfNumber, sourceAddrNpi: NumericPlanIndicator,
                             sourceAddr: COctetString,  esmeAddrTon: TypeOfNumber,
                             esmeAddrNpi: NumericPlanIndicator, esmeAddr: COctetString,
                             msAvailabilityStatus: Option[Tlv]) extends Pdu(CommandId.alert_notification) with NullCommandStatus
                             with WritePdu.AlertNotificationWriter
