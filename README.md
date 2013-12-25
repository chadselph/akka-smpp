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

## Example Usage
See Demo.scala for an example usage of an SMPP Client 

```scala
  val client = actorSystem.actorOf(Props(new SmppClient(new InetSocketAddress("localhost", 2775))))
  client ! EnquireLink(16, 4)
```
