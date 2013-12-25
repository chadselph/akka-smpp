package akkasmpp.protocol.bytestrings

import akka.util.{ByteStringBuilder, ByteString}
import akkasmpp.protocol.{AlertNotification, ReplaceSm, CancelSm, QuerySmResp, QuerySm, DataSmResp, DataSm, SubmitMultiResp, SubmitMulti, SmRespLike, SmLike, Outbind, BindRespLike, BindLike, Pdu}
import SmppByteString.Builder

/**
 * Write PDUs into bytestrings
 */

object WritePdu {

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN
  implicit val charset = java.nio.charset.Charset.forName("ASCII")

  def writeHeader(p: Pdu) = {
    val bsb = new ByteStringBuilder
    bsb.putInt(p.commandLength)
    bsb.putCommandId(p.commandId)
    bsb.putCommandStatus(p.commandStatus)
    bsb.putInt(p.sequenceNumber)
  }

  trait BindWriter { this: BindLike =>

    def toByteString = {
      val bsb = writeHeader(this)
      bsb.putCOctetString(systemId)
      bsb.putCOctetString(password)
      bsb.putCOctetString(systemType.getOrElse(""))
      bsb.putByte(interfaceVersion)
      bsb.putTypeOfNumber(addrTon)
      bsb.putNumberPlanIndicator(addrNpi)
      bsb.putCOctetString("")  // addressRange
      println(bsb.result())
      bsb.result()
    }
  }

  trait BindRespWriter { this: BindRespLike =>
    def toByteString = {
      val bsb = writeHeader(this)
      if (this.commandStatus.id == 0) {
        /* only return body for command_status 0 */
        bsb.putCOctetString(systemId.getOrElse(Array.empty))
        if (scInterfaceVersion.isDefined) {
          bsb.putTlv(scInterfaceVersion.get)
        }
      }
      bsb.result()
    }

  }
  trait OutbindWriter { this: Outbind =>
    def toByteString = {
      val bsb = writeHeader(this)
      bsb.putCOctetString(systemId)
      bsb.putCOctetString(password)
      bsb.result()
    }

  }


  trait HeaderOnlyWriter { this: Pdu =>
    def toByteString = writeHeader(this).result()
  }

  trait SmWriter { this: SmLike =>
    def toByteString = {
      val bsb = writeHeader(this)
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

  trait SmRespWriter { this: SmRespLike =>
    def toByteString = {
      val bsb = writeHeader(this)
      if (commandStatus.id == 0) {
        bsb.putCOctetString(messageId)
      }
      bsb.result()
    }
  }

  trait SubmitMultiWriter { this: SubmitMulti =>
    def toByteString = {
      val bsb = writeHeader(this)
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

  trait SubmitMultiRespWriter { this: SubmitMultiResp =>
    def toByteString = {
      val bsb = writeHeader(this)
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

  trait DataSmWriter { this: DataSm =>
    def toByteString = {
      val bsb = writeHeader(this)
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

  trait DataSmRespWriter { this: DataSmResp =>
    def toByteString = {
      val bsb = writeHeader(this)
      bsb.putCOctetString(messageId)
      for (tlv <- tlvs) {
        bsb.putTlv(tlv)
      }
      bsb.result()
    }
  }

  trait QuerySmWriter { this: QuerySm =>
    def toByteString = {
      val bsb = writeHeader(this)
      bsb.putCOctetString(messageId)
      bsb.putTypeOfNumber(sourceAddrTon)
      bsb.putNumberPlanIndicator(sourceAddrNpi)
      bsb.putCOctetString(sourceAddr)
      bsb.result()
    }
  }

  trait QuerySmRespWriter { this: QuerySmResp =>
    def toByteString = {
      val bsb = writeHeader(this)
      bsb.putCOctetString(messageId)
      bsb.putTime(finalDate)
      bsb.putMessageState(messageState)
      bsb.putByte(errorCode.toByte)
      bsb.result()
    }
  }

  trait CancelSmWriter { this: CancelSm =>
    def toByteString = {
      val bsb = writeHeader(this)
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

  trait ReplaceSmWriter { this: ReplaceSm =>
    def toByteString = {
      val bsb = writeHeader(this)
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

  trait AlertNotificationWriter { this: AlertNotification =>
    def toByteString = {
      val bsb = writeHeader(this)
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
