## Akka SMPP Server
Impementation of SMPP 3.4 in Akka using akka-streams.

Currently this is pretty bare-bones; constants have been mostly defined.
Case classes exist for all the PDUs. Parsing PDUs from TCP and
serializing them works.

## Installing with sbt

The artifacts are currently just hosted on my bintray; cross-built for Scala 2.11 and 2.12.

```
resolvers += Resolver.bintrayRepo("chadselph", "maven")

libraryDependencies +=  "me.chadrs" %% "akka-smpp" % "0.4.1"
```

should do the trick.

## Example Usage

SMPP is implemented as an akka extension and is inspired by akka-http/spray.

To start an SMPP server:

```scala
implicit val system = ActorSystem()
implicit val mat = ActorMaterializer()

val pduEcho: Flow[EsmeRequest, SmscResponse, Unit].map(identity)

val binding = Smpp(system).listen(interface = "", port = 2775)
binding.connections.foreach { connection =>
  connection.handle(pduEcho)
}
```

If you don't want to create a Flow manually, you can create a simple one
using the SMPPServerFlow:

```scala

class MyServerFlow extends SmppServerFlow {
  override def handleBindTransmitter(bindRequest: BindTransmitter): Future[BindTransmitterResp] = future {
    if(bindRequest.systemId == "admin") {
        BindTransmitterResp(CommandStatus.ESME_ROK, bindRequest.sequenceNumber, None, None)
    } else {
        BindTransmitterResp(CommandStatus.ESME_EINVSMSCID, bindRequest.sequenceNumber, None, None)
    }
  }

  override def handleSubmitSm(submitSm): Future[SubmitSmResp] = {

    val msgId = "123412"
    system.dispatcher.scheduler(5.seconds) {
      val f = this.submitDeliverSm(...) // fake delivery receipt
      f.onComplete {
        case Success(rsp: SubmitSmResp) => // esme got dlr
        case Failure(ex) => // maybe retry or something
      }
    }
    Future.successful(SubmitSmResp(CommandStatus.ESME_ROK, submitSm.sequenceNumber, Some(msgId))
  }
}

```

Each of the methods return a Future of the response type to allow for async or synchronous
responses. If the future fails with an `SmppProtocolException(commandStatus)`, then it
will return a `GenericNack` with `commandStatus` as the error. If the future fails any other way,
the response PDU will be a `GenericNack` with error of ESME_RSYSERR.

To change this behavior, override `handleError` which is a `PartialFunction[(Exception, EsmeRequest), SmscResponse]`


## Todo
- Parse valid TLVs
- Framework for validating Bind / BindResp in server
- Switch out Enumeration for something better with AnyVal
- More PDU Builders
- Make examples work
- Language for SMPP test suites


## SMPP Test Suites (TODO, documentation driven development)

Suppose you want to verify the behavior of your vendor for some particular
edge case or strange scenario. This section is for you. 

```scala

withBoundClient("host", 2775, "user", "pass") { client =>
  val sn1 = client.getNextSequenceNumber
  val submitSm = PduBuilder.submitSm(to="my-handset-number", from="+12345678900", shortMessage=OctectString.ascii("2 msg with same seq#"))
  client.send(submitSm(sn1))
  client.send(submitSm(sn1))
  // did we receive the message twice on our handset?
  
  val sn2 = client.getNextSequenceNumber
  val submitSm3 = submitSm2.copy(shortMessage = OctetString.ascii("same seq number, but different content"))
}

```

## Coding Style

For the first version of this library, the coding style is purposely very simple.
This leads to a certain amount of boilerplate that I would eventually like to rewrite
to be more idiomatically-Scala. But the goal for now is that a Java or Python could
easily understand all the code without learning many magic Scala features.

That being said, it is Scala idiomatic in terms of preferring immutable data, avoiding `null`, and
providing Async APIs.

## PDU Builders

PDUs like SubmitSm and DeliverSm have a lot of parameters, and very few that actually matter. I didn't like
the idea of having the protocol case classes be opinionated enough to have "default" values, and this quickly
became a pain point of using the API. Instead, reasonable defaults now live in the `akkasmpp.protocol.PduBuilder`.

```scala
  import akkasmpp.protocol.PduBuilder
  import akkasmpp.protocol.ParameterTypes.{TypeOfNumber, COctetString, OctetString}

  val source: COctetString = COctetString
  val dest: COctetString =
  val msg: OctetString = OctetString(99, 104, 0, 100)
  val builder = new PduBuilder(defaultTypeOfNumber = TypeOfNumber.International) // override any defaults you want in here with by-name parameters
  builder.submitSm(sourceAddr = source, destinationAddr = dest, shortMessage = msg) // also lets you override anything
```

## Is it "production ready"?

Probably not. It has been used for internal tools and load testing, but as far as I know,
not in any production scale, customer-facing systems. Because it has been used as a tool
for inter-op testing, it has successfully parsed and sent PDUs to/from around one hundred
carriers / SMS aggregators.

If you've used it to build something bigger, feel free to reach out and let me know what problems you ran into.

# Similar Projects
There already exist several SMPP libraries in Java. To my knowledge, this is the only one in Scala.

- [Cloudhopper](https://github.com/twitter/cloudhopper-smpp) is a nice async (netty) library that I've used from Scala before
  but suffers from very mutable APIs and lack of type safety. Fine for Java, but in Scala, this doesn't feel natural.
- [jsmpp](https://github.com/uudashr/jsmpp) is a blocking Java API for SMPP.
- [OpenSMPP](https://github.com/OpenSmpp/opensmpp) Similar to JSMPP and also lacks immutable PDUs.
