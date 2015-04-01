## Akka SMPP Server
Impementation of SMPP 3.4 in Akka using akka-io.

Currently this is pretty bare-bones; constants have been mostly defined.
Case classes exist for all the PDUs. Parsing PDUs from TCP and
serializing them works.


### Todo
- Parse valid TLVs
- Framework for validating Bind / BindResp in server
- Switch out Enumeration for something better with AnyVal
- More PDU Builders
- Rewrite TCP layer in new akka-stream

## Example Usage
See Demo.scala for an example usage of an SMPP Client 

```scala
  val client = actorSystem.actorOf(Props(new SmppClient(new InetSocketAddress("localhost", 2775))))
  client ! EnquireLink(16, 4)
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

# Similar Projects
There already exist several SMPP libraries in Java. To my knowledge, this is the only one in Scala.

- [Cloudhopper](https://github.com/twitter/cloudhopper-smpp) is a nice async (netty) library that I've used from Scala before
  but suffers from very mutable APIs and lack of type safety. Fine for Java, but in Scala, this doesn't feel natural.
- [jsmpp](https://github.com/uudashr/jsmpp) is a blocking Java API for SMPP.
- [OpenSMPP](https://github.com/OpenSmpp/opensmpp) Similar to JSMPP and also lacks immutable PDUs.
