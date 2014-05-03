package akkasmpp.protocol.bytestrings

import akka.util.{ByteStringBuilder, ByteString}
import akkasmpp.protocol.{COctetString, AlertNotification, ReplaceSm, CancelSm, QuerySmResp, QuerySm, DataSmResp, DataSm, SubmitMultiResp, SubmitMulti, SmRespLike, SmLike, Outbind, BindRespLike, BindLike, Pdu}
import SmppByteString.Builder

/**
 * Write PDUs into bytestrings
 */

object WritePdu {

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN
  implicit val charset = java.nio.charset.Charset.forName("ASCII")

  trait PduWriter { this: Pdu =>
    def writeBody(b: ByteStringBuilder): ByteString
    def toByteString = {
      val bsb = writeHeader()
      val bsbBody = writeBody(bsb)
      buildAndAddLength(bsbBody)

    }
    /**
     * Writes the header (except length)
     */
    private def writeHeader() = {
      val bsb = new ByteStringBuilder
      bsb.putCommandId(commandId)
      bsb.putCommandStatus(commandStatus)
      bsb.putInt(sequenceNumber)
      bsb
    }
    /**
     * add the length
     * @param bsb ByteString with header (except length) and body of PDU
     * @return
     */
    private def buildAndAddLength(bsb: ByteString) = {
      val len = new ByteStringBuilder().putInt(bsb.length + 4).result()
      len.concat(bsb)
    }
  }

  def writeHeader(p: Pdu) = {
  }

  trait BindWriter extends PduWriter { this: BindLike =>

    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putCOctetString(systemId)
      bsb.putCOctetString(password)
      bsb.putCOctetString(systemType)
      bsb.putByte(interfaceVersion)
      bsb.putTypeOfNumber(addrTon)
      bsb.putNumberPlanIndicator(addrNpi)
      bsb.putCOctetString("")  // addressRange
      bsb.result()
    }
  }

  trait BindRespWriter extends PduWriter { this: BindRespLike =>
    def writeBody(bsb: ByteStringBuilder) = {
      if (this.commandStatus.id == 0) {
        /* only return body for command_status 0 */
        systemId.foreach { system =>
          bsb.putCOctetString(system)
          scInterfaceVersion.foreach { sci =>
            bsb.putTlv(sci)
          }
        }
      }
      bsb.result()
    }
  }
  trait OutbindWriter extends PduWriter { this: Outbind =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putCOctetString(systemId)
      bsb.putCOctetString(password)
      bsb.result()
    }

  }


  trait HeaderOnlyWriter extends PduWriter { this: Pdu =>
    def writeBody(bsb: ByteStringBuilder) = bsb.result()
  }

  trait SmWriter extends PduWriter { this: SmLike =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putServiceType(serviceType)
      bsb.putTypeOfNumber(sourceAddrTon)
      bsb.putNumberPlanIndicator(sourceAddrNpi)
      bsb.putCOctetString(sourceAddr)
      bsb.putTypeOfNumber(destAddrTon)
      bsb.putNumberPlanIndicator(destAddrNpi)
      bsb.putCOctetString(destinationAddr)
      bsb.putEsmClass(esmClass)
      bsb.putByte(protocolId)
      bsb.putPriority(priorityFlag)
      bsb.putTime(scheduleDeliveryTime)
      bsb.putTime(validityPeriod)
      bsb.putRegisteredDelivery(registeredDelivery)
      bsb.putByte(if (replaceIfPresentFlag) 1 else 0)
      bsb.putDataCodingScheme(dataCoding)
      bsb.putByte(smDefaultMsgId)
      bsb.putByte(smLength.toByte)
      bsb.putOctetString(shortMessage)
      for (tlv <- tlvs) {
        bsb.putTlv(tlv)
      }
      bsb.result()
    }
  }

  trait SmRespWriter extends PduWriter { this: SmRespLike =>
    def writeBody(bsb: ByteStringBuilder) = {
      messageId.foreach { mid =>
        bsb.putCOctetString(mid)
      }
      bsb.result()
    }
  }

  trait SubmitMultiWriter extends PduWriter { this: SubmitMulti =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putServiceType(serviceType)
      bsb.putTypeOfNumber(sourceAddrTon)
      bsb.putNumberPlanIndicator(sourceAddrNpi)
      bsb.putCOctetString(sourceAddr)
      bsb.putByte(numberOfDests.toByte)
      for ((dTon, dNpi, d) <- destAddresses) {
        bsb.putTypeOfNumber(dTon)
        bsb.putNumberPlanIndicator(dNpi)
        bsb.putCOctetString(d)
      }
      bsb.putEsmClass(esmClass)
      bsb.putByte(protocolId)
      bsb.putPriority(priorityFlag)
      bsb.putTime(scheduleDeliveryTime)
      bsb.putTime(validityPeriod)
      bsb.putRegisteredDelivery(registeredDelivery)
      bsb.putByte(if (replaceIfPresentFlag) 1 else 0)
      bsb.putDataCodingScheme(dataCoding)
      bsb.putByte(smDefaultMsgId)
      bsb.putByte(smLength.toByte)
      bsb.putOctetString(shortMessage)
      for (tlv <- tlvs) {
        bsb.putTlv(tlv)
      }
      bsb.result()
    }
  }

  trait SubmitMultiRespWriter extends PduWriter { this: SubmitMultiResp =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putCOctetString(messageId)
      bsb.putByte(noUnsuccess)
      for ((ton, npi, addr, err) <- unsuccessSmes) {
        bsb.putTypeOfNumber(ton)
        bsb.putNumberPlanIndicator(npi)
        bsb.putCOctetString(addr)
        bsb.putCommandStatus(err)
      }
      bsb.result()
    }
  }

  trait DataSmWriter extends PduWriter { this: DataSm =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putServiceType(serviceType)
      bsb.putTypeOfNumber(sourceAddrTon)
      bsb.putNumberPlanIndicator(sourceAddrNpi)
      bsb.putCOctetString(sourceAddr)
      bsb.putTypeOfNumber(destAddrTon)
      bsb.putNumberPlanIndicator(destAddrNpi)
      bsb.putCOctetString(destinationAddr)
      bsb.putEsmClass(esmClass)
      bsb.putRegisteredDelivery(registeredDelivery)
      bsb.putDataCodingScheme(dataCoding)
      for (tlv <- tlvs) {
        bsb.putTlv(tlv)
      }
      bsb.result()
    }
  }

  trait DataSmRespWriter extends PduWriter { this: DataSmResp =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putCOctetString(messageId)
      for (tlv <- tlvs) {
        bsb.putTlv(tlv)
      }
      bsb.result()
    }
  }

  trait QuerySmWriter extends PduWriter { this: QuerySm =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putCOctetString(messageId)
      bsb.putTypeOfNumber(sourceAddrTon)
      bsb.putNumberPlanIndicator(sourceAddrNpi)
      bsb.putCOctetString(sourceAddr)
      bsb.result()
    }
  }

  trait QuerySmRespWriter extends PduWriter { this: QuerySmResp =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putCOctetString(messageId)
      bsb.putTime(finalDate)
      bsb.putMessageState(messageState)
      bsb.putByte(errorCode.toByte)
      bsb.result()
    }
  }

  trait CancelSmWriter extends PduWriter { this: CancelSm =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putServiceType(serviceType)
      bsb.putCOctetString(messageId)
      bsb.putTypeOfNumber(sourceAddrTon)
      bsb.putNumberPlanIndicator(sourceAddrNpi)
      bsb.putCOctetString(sourceAddr)
      bsb.putTypeOfNumber(destAddrTon)
      bsb.putNumberPlanIndicator(destAddrNpi)
      bsb.putCOctetString(destinationAddr)
      bsb.result()
    }
  }

  trait ReplaceSmWriter extends PduWriter { this: ReplaceSm =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putCOctetString(messageId)
      bsb.putTypeOfNumber(sourceAddrTon)
      bsb.putNumberPlanIndicator(sourceAddrNpi)
      bsb.putCOctetString(sourceAddr)
      bsb.putTime(scheduleDeliveryTime)
      bsb.putTime(validityPeriod)
      bsb.putRegisteredDelivery(registeredDelivery)
      bsb.putByte(smDefaultMsgId)
      bsb.putByte(smLength.toByte)
      bsb.putOctetString(shortMessage)
      bsb.result()
    }
  }

  trait AlertNotificationWriter extends PduWriter { this: AlertNotification =>
    def writeBody(bsb: ByteStringBuilder) = {
      bsb.putTypeOfNumber(sourceAddrTon)
      bsb.putNumberPlanIndicator(sourceAddrNpi)
      bsb.putCOctetString(sourceAddr)
      bsb.putTypeOfNumber(esmeAddrTon)
      bsb.putNumberPlanIndicator(esmeAddrNpi)
      bsb.putCOctetString(esmeAddr)
      msAvailabilityStatus foreach { tlv =>
        bsb.putTlv(tlv)
      }
      bsb.result()
    }
  }

}
