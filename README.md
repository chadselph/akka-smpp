## Akka SMPP Server
Impementation of SMPP 3.4 in Akka using akka-io.

Currently this is pretty bare-bones; constants have been mostly defined.
Case classes exist for all the PDUs. Parsing PDUs from TCP and
serializing them works.


### Todo
- Tests
- EnquireLink timer
- Reading incoming PDUs
- Several parameter types need write implementations
- Solve commandLength problem
- Server example
- Parse valid TLVs

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

# Similar Projects
There already exist several SMPP libraries in Java. To my knowledge, this is the only one in Scala.

- [Cloudhopper](https://github.com/twitter/cloudhopper-smpp) is a nice async (netty) library that I've used from Scala before
  but suffers from very mutable APIs and lack of type safety. Fine for Java, but in Scala, this doesn't feel natural.
- [jsmpp](https://github.com/uudashr/jsmpp) is a blocking Java API for SMPP.
- [OpenSMPP](https://github.com/OpenSmpp/opensmpp) Similar to JSMPP and also lacks immutable PDUs.
