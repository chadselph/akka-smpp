package akkasmpp.protocol.auth

import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.protocol._
import akkasmpp.protocol.auth.BindRequest.BindRespConstructor

/**
 * Abstracts BindTransmitter/BindReceiver/BindTransceiver into one class.
 *
 * Usage:
 *
 * BindRequest( somePdu ).respondOk()
 */
case class BindRequest private(requestsTransmit: Boolean, requestsReceive: Boolean,
                       systemId: COctetString, password: COctetString,
                       original: BindLike, replyContructor: BindRespConstructor) {
  def respond(commandStatus: CommandStatus,
                systemType: Option[COctetString] = None,
                scInterfaceVersion: Option[Tlv] = None) = {
    val pdu = replyContructor(commandStatus, original.sequenceNumber, systemType, scInterfaceVersion)
    if (commandStatus == CommandStatus.ESME_ROK) {
      BindResponseSuccess(pdu)
    } else {
      BindResponseError(pdu)
    }
  }

  def respondOk(systemType: Option[COctetString] = None,
              scInterfaceVersion: Option[Tlv] = None) = {
    respond(CommandStatus.ESME_ROK, systemType, scInterfaceVersion)
  }
}

object BindRequest {

  type BindRespConstructor = (CommandStatus, SmppTypes.Integer, Option[COctetString], Option[Tlv]) => BindRespLike

  def apply(bindTransmitter: BindTransmitter) =
    new BindRequest(true, false, bindTransmitter.systemId, bindTransmitter.password,
      bindTransmitter, BindTransmitterResp)

  def apply(bindReceiver: BindReceiver) =
    new BindRequest(true, false, bindReceiver.systemId, bindReceiver.password,
      bindReceiver, BindReceiverResp)

  def apply(bindTransceiver: BindTransceiver) =
    new BindRequest(true, false, bindTransceiver.systemId, bindTransceiver.password,
      bindTransceiver, BindTransceiverResp)

  def fromBindLike(bindLike: BindLike) = {
    bindLike match {
      case br: BindReceiver => BindRequest(br)
      case bt: BindTransmitter => BindRequest(bt)
      case bt: BindTransceiver => BindRequest(bt)
    }
  }
}

sealed trait BindResponse { val pdu: BindRespLike }
case class BindResponseSuccess private[auth] (pdu: BindRespLike) extends BindResponse
case class BindResponseError private[auth] (pdu: BindRespLike) extends BindResponse
