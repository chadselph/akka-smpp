package akkasmpp.protocol

import akka.event.{LookupClassification, EventBus}
import akka.actor.ActorRef

trait PduLogger {
  def logIncoming(pdu: Pdu)
  def logOutgoing(pdu: Pdu)
}

object PduLogger {
  /*
  Default logger doesn't do anything.
   */
  val default = new PduLogger {
    override def logOutgoing(pdu: Pdu): Unit = ()
    override def logIncoming(pdu: Pdu): Unit = ()
  }


  /*
  Logs pdus over the akka eventbus
   */
  def eventBus(bind: String) = new PduLogger {
    import PduEventBusLogger._
    override def logIncoming(pdu: Pdu): Unit = PduEventBus.publish(PduEvent(bind, Incoming, pdu))
    override def logOutgoing(pdu: Pdu): Unit = PduEventBus.publish(PduEvent(bind, Outgoing, pdu))
  }
}

object PduEventBusLogger {

  sealed trait Direction
  case object Outgoing extends Direction
  case object Incoming extends Direction
  case class PduEvent(bind: String, direction: Direction, pdu: Pdu)

  object PduEventBus extends EventBus with LookupClassification {
    override type Event = PduEvent
    override type Classifier = String
    override type Subscriber = ActorRef

    override protected def publish(event: Event, subscriber: Subscriber): Unit = {
      subscriber ! event
    }

    override protected def mapSize(): Int = 16
    override protected def classify(event: Event): Classifier = event.bind
    override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = a.compareTo(b)
  }

}
