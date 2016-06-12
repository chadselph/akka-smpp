package akkasmpp.protocol

import akkasmpp.testutil.ByteStringHelpers
import akkasmpp.protocol.bytestrings.SmppByteString.{Builder, Iterator}
import org.scalatest.{FlatSpec, Matchers}

class SmppByteStringTest
    extends FlatSpec
    with Matchers
    with ByteStringHelpers {

  import EsmClass.{Features, MessageType, MessagingMode}

  "Esm classes" should "parse correctly" in {
    parsingTheByte(0).getEsmClass should be(
        EsmClass(MessagingMode.Default, MessageType.NormalMessage))
    parsingTheByte(202.toByte).getEsmClass should be(
        EsmClass(MessagingMode.Forward,
                 MessageType.DeliveryAcknowledgement,
                 Features.UDHIIndicator,
                 Features.SetReplyPath))
  }

  "Esm classes " should "serialize correctly" in {
    withByteString { bsb =>
      bsb.putEsmClass(
          EsmClass(MessagingMode.Default, MessageType.NormalMessage))
    } andThenCheck { bi =>
      bi.len should be(1)
      bi.getByte should be(0)
    }

    withByteString { bsb =>
      bsb.putEsmClass(EsmClass(MessagingMode.DataGram,
                               MessageType.ManualUserAcknowledgement,
                               Features.SetReplyPath,
                               Features.UDHIIndicator))
    } andThenCheck { bi =>
      bi.len should be(1)
      bi.getByte should be((1 + 16 + 128 + 64).toByte)
    }
  }

  "Registered delivery" should "parse correctly" in {
    import RegisteredDelivery._
    parsingTheByte(0).getRegisteredDelivery should be(RegisteredDelivery())
    parsingTheByte(29).getRegisteredDelivery should be
    RegisteredDelivery(SmscDelivery.FailureRequested,
                       SmeAcknowledgement.BothRequested,
                       IntermediateNotification.Requested)
  }

  "Registered delivery " should "serialize correct" in {
    import RegisteredDelivery._
    withByteString { bsb =>
      bsb.putRegisteredDelivery(RegisteredDelivery())
    } andThenCheck { bi =>
      bi.len should be(1)
      bi.getByte should be(0)
    }

    withByteString { bsb =>
      bsb.putRegisteredDelivery(
          RegisteredDelivery(SmscDelivery.SuccessAndFailureRequested,
                             SmeAcknowledgement.ManualUserRequested,
                             IntermediateNotification.Requested))
    } andThenCheck { bi =>
      bi.len should be(1)
      bi.getByte should be(25)
    }
  }
}
