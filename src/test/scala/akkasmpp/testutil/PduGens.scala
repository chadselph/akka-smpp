package akkasmpp.testutil

import akkasmpp.protocol.EsmClass.{Features, MessageType, MessagingMode}
import akkasmpp.protocol.RegisteredDelivery.{IntermediateNotification, SmeAcknowledgement, SmscDelivery}
import akkasmpp.protocol.{COctetString, CommandStatus, DataCodingScheme, DeliverSm, DeliverSmResp, EsmClass, NullTime, NumericPlanIndicator, OctetString, Priority, RegisteredDelivery, ServiceType, SubmitSm, SubmitSmResp, TypeOfNumber}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

/**
  * scalacheck for SMPP PDUs
 */
object PduGens {

  private def strGenToCOctetGen(g: Gen[String]) = g.map(COctetString.utf8)


  val serviceTypeGen = for {
    str <- Gen.alphaStr
  } yield ServiceType.value(str)

  val numberGen = for {
    typeOfNumber <- Gen.oneOf(TypeOfNumber.values.toSeq)
    numericPlanIndicator <- Gen.oneOf(NumericPlanIndicator.values.toSeq)
    addr <- strGenToCOctetGen(Gen.numStr)
  } yield (typeOfNumber, numericPlanIndicator, addr)

  val esmClassGen = for {
    mm <- Gen.oneOf(MessagingMode.values.toSeq)
    mt <- Gen.oneOf(MessageType.values.toSeq)
    features <- Gen.someOf(Features.values.toSeq).map(Features.ValueSet(_:_*))
  } yield EsmClass(mm, mt, features)

  val registeredDeliveryGen = for {
    smsc <- Gen.oneOf(SmscDelivery.values.toSeq)
    sme <- Gen.oneOf(SmeAcknowledgement.values.toSeq)
    intermed <- Gen.oneOf(IntermediateNotification.values.toSeq)
  } yield RegisteredDelivery.apply(smsc, sme, intermed)

  val commandStatusGen = Gen.oneOf(CommandStatus.ESME_ROK, CommandStatus.ESME_RINVDLNAME, CommandStatus.ESME_RBINDFAIL)

  val submitSmGen = for {
    seqN <- arbitrary[Int]
    serviceType <- serviceTypeGen
    (sourceTon, sourceNpi, sourceAddr) <- numberGen
    (destTon, destNpi, destAddr) <- numberGen
    esmClass <- esmClassGen
    protocol <- arbitrary[Byte]
    priority <- Gen.oneOf(Priority.values.toSeq)
    registeredDel <- registeredDeliveryGen
    replaceIfPresent <- Gen.oneOf(true, false)
    dcs <- Gen.oneOf(DataCodingScheme.values.toSeq)
    smDefaultMsgId <- arbitrary[Byte]
    msg <- arbitrary[String].withFilter(_.getBytes.length < 160)
    msgBytes = msg.getBytes

  } yield SubmitSm(seqN, serviceType, sourceTon, sourceNpi, sourceAddr, destTon, destNpi, destAddr,
    esmClass, protocol, priority, NullTime, NullTime, registeredDel, replaceIfPresent, dcs, smDefaultMsgId,
    msgBytes.length.toByte, OctetString.fromBytes(msgBytes), Nil)

  val deliverSmGen = for {
    seqN <- arbitrary[Int]
    serviceType <- serviceTypeGen
    (sourceTon, sourceNpi, sourceAddr) <- numberGen
    (destTon, destNpi, destAddr) <- numberGen
    esmClass <- esmClassGen
    protocol <- arbitrary[Byte]
    priority <- Gen.oneOf(Priority.values.toSeq)
    registeredDel <- registeredDeliveryGen
    replaceIfPresent <- Gen.oneOf(true, false)
    dcs <- Gen.oneOf(DataCodingScheme.values.toSeq)
    smDefaultMsgId <- arbitrary[Byte]
    msg <- arbitrary[String].withFilter(_.getBytes.length < 160)
    msgBytes = msg.getBytes
  } yield DeliverSm(seqN, serviceType, sourceTon, sourceNpi, sourceAddr, destTon, destNpi, destAddr,
    esmClass, protocol, priority, NullTime, NullTime, registeredDel, replaceIfPresent, dcs, smDefaultMsgId,
    msgBytes.length.toByte, OctetString.fromBytes(msgBytes), Nil)

  val submitSmRespGen = for {
    commandStatus <- commandStatusGen
    seqN <- arbitrary[Int]
    msgId <- arbitrary[String].withFilter(_.getBytes.length < 255)
    msgIdO = if (commandStatus != CommandStatus.ESME_ROK) None else Some(COctetString.utf8(msgId))

  } yield SubmitSmResp(commandStatus, seqN, msgIdO)

  val deliverSmRespGen = for {
    commandStatus <- commandStatusGen
    seqN <- arbitrary[Int]
    msgId <- arbitrary[String].withFilter(_.getBytes.length < 255)
    msgIdO = if (commandStatus != CommandStatus.ESME_ROK) None else Some(COctetString.utf8(msgId))

  } yield DeliverSmResp(commandStatus, seqN, msgIdO)

  val pduGen = Gen.oneOf(submitSmGen, deliverSmGen, submitSmRespGen, deliverSmRespGen)

}
