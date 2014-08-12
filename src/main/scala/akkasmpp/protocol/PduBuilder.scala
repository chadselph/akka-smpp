package akkasmpp.protocol

import akkasmpp.protocol.SmppTypes.SequenceNumber

/**
 * Higher level API for creating PDUs when you don't want to specify 1000 paramaters.
 * Create a builder once and let it hold onto your defaults.
 */
case class PduBuilder(defaultServiceType: ServiceType.ServiceType = ServiceType.Default,
                      defaultTypeOfNumber: TypeOfNumber.TypeOfNumber = TypeOfNumber.International,
                      defaultNumericPlanIndicator: NumericPlanIndicator.NumericPlanIndicator = NumericPlanIndicator.E164,
                      defaultEsmClass: EsmClass = EsmClass(EsmClass.MessagingMode.Default, EsmClass.MessageType.NormalMessage),
                      defaultProtocolId: Byte = 0x34,
                      defaultPriority: Priority.Priority = Priority.Level0,
                      defaultRegisteredDelivery: RegisteredDelivery = RegisteredDelivery(),
                      defaultReplaceIfPresentFlag: Boolean = false,
                      defaultDataCodingScheme: DataCodingScheme.DataCodingScheme = DataCodingScheme.SmscDefaultAlphabet,
                      defaultTlvs: List[Tlv] = Nil
                       ) {


  def submitSm(serviceType: ServiceType.ServiceType = defaultServiceType,
               sourceAddrTon: TypeOfNumber.TypeOfNumber = defaultTypeOfNumber,
               sourceAddrNpi: NumericPlanIndicator.NumericPlanIndicator = defaultNumericPlanIndicator,
               sourceAddr: COctetString,
               destAddrTon: TypeOfNumber.TypeOfNumber = defaultTypeOfNumber,
               destAddrNpi: NumericPlanIndicator.NumericPlanIndicator = defaultNumericPlanIndicator,
               destinationAddr: COctetString,
               esmClass: EsmClass = defaultEsmClass,
               protocolId: Byte = defaultProtocolId,
               priorityFlag: Priority.Priority = defaultPriority,
               scheduleDeliveryTime: TimeFormat = NullTime,
               validityPeriod: TimeFormat = NullTime,
               registeredDelivery: RegisteredDelivery = defaultRegisteredDelivery,
               replaceIfPresentFlag: Boolean = defaultReplaceIfPresentFlag,
               dataCoding: DataCodingScheme.DataCodingScheme = defaultDataCodingScheme,
               smDefaultMsgId: Byte = 0,
               shortMessage: OctetString,
               tlvs: List[Tlv] = defaultTlvs
                ): (SequenceNumber) => SubmitSm = {
    SubmitSm(_, serviceType, sourceAddrTon, sourceAddrNpi, sourceAddr, destAddrTon, destAddrNpi, destinationAddr,
      esmClass, protocolId, priorityFlag, scheduleDeliveryTime, validityPeriod, registeredDelivery, replaceIfPresentFlag,
      dataCoding, smDefaultMsgId, shortMessage.size.toByte, shortMessage, tlvs)
  }

  def deliverSm(serviceType: ServiceType.ServiceType = defaultServiceType,
               sourceAddrTon: TypeOfNumber.TypeOfNumber = defaultTypeOfNumber,
               sourceAddrNpi: NumericPlanIndicator.NumericPlanIndicator = defaultNumericPlanIndicator,
               sourceAddr: COctetString,
               destAddrTon: TypeOfNumber.TypeOfNumber = defaultTypeOfNumber,
               destAddrNpi: NumericPlanIndicator.NumericPlanIndicator = defaultNumericPlanIndicator,
               destinationAddr: COctetString,
               esmClass: EsmClass = defaultEsmClass,
               protocolId: Byte = defaultProtocolId,
               priorityFlag: Priority.Priority = defaultPriority,
               scheduleDeliveryTime: TimeFormat = NullTime,
               validityPeriod: TimeFormat = NullTime,
               registeredDelivery: RegisteredDelivery = defaultRegisteredDelivery,
               replaceIfPresentFlag: Boolean = defaultReplaceIfPresentFlag,
               dataCoding: DataCodingScheme.DataCodingScheme = defaultDataCodingScheme,
               smDefaultMsgId: Byte = 0,
               shortMessage: OctetString,
               tlvs: List[Tlv] = defaultTlvs
                ): (SequenceNumber) => DeliverSm = {
    DeliverSm(_, serviceType, sourceAddrTon, sourceAddrNpi, sourceAddr, destAddrTon, destAddrNpi, destinationAddr,
      esmClass, protocolId, priorityFlag, scheduleDeliveryTime, validityPeriod, registeredDelivery, replaceIfPresentFlag,
      dataCoding, smDefaultMsgId, shortMessage.size.toByte, shortMessage, tlvs)
  }

}
