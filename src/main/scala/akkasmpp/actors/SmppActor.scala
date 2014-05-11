package akkasmpp.actors

import akka.actor.{ActorRef, Actor}
import akkasmpp.protocol.SequenceNumberGenerator
import akkasmpp.protocol.SmppTypes.SequenceNumber

trait SmppActor extends Actor {
  def sequenceNumberGen: SequenceNumberGenerator
  def window: Map[SequenceNumber, ActorRef]
}
