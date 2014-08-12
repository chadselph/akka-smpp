import akkasmpp.protocol.EsmClass.{Features, MessageType, MessagingMode}
import akkasmpp.protocol.RegisteredDelivery.{IntermediateNotification, SmeAcknowledgement, SmscDelivery}
import akkasmpp.protocol.{COctetString, DataCodingScheme, EsmClass, NullTime, NumericPlanIndicator, OctetString, Priority, RegisteredDelivery, ServiceType, SubmitSm, TypeOfNumber}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

/**
 * Created by chad on 8/11/14.
 */
object PduGens {

  private def strGenToCOctetGen(g: Gen[String]) = {
    g.map(new COctetString(_)(java.nio.charset.Charset.forName("ASCII")))
  }


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
    msg <- arbitrary[String]
    msgBytes = msg.getBytes

  } yield SubmitSm(seqN, serviceType, sourceTon, sourceNpi, sourceAddr, destTon, destNpi, destAddr,
    esmClass, protocol, priority, NullTime, NullTime, registeredDel, replaceIfPresent, dcs, smDefaultMsgId,
    msgBytes.length.toByte, new OctetString(msgBytes), Nil)

  val pduGen = submitSmGen

}
