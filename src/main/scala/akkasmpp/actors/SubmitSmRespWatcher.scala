package akkasmpp.actors

import akkasmpp.protocol.SmppTypes.{MessageId, SequenceNumber}
import akka.actor.{Props, Actor, ActorRef}
import akkasmpp.protocol.SubmitSmResp
import akkasmpp.protocol.CommandStatus.CommandStatus
import akkasmpp.actors.SmppClient.SendMessageAck

object SubmitSmRespWatcher {
  def props(seqs: Set[SequenceNumber], actor: ActorRef) = Props(classOf[SubmitSmRespWatcher], seqs, actor)
}

class SubmitSmRespWatcher(sequenceNumbers: Set[SequenceNumber], forwardTo: ActorRef) extends Actor {

  implicit val encoding = java.nio.charset.Charset.forName("UTF-8")
  var result: Seq[(CommandStatus, Option[String])] = Nil
  var waitingOn = sequenceNumbers

  // XXX: set timer and ask parent to retry

  def receive: Actor.Receive = {
    case SubmitSmResp(commandStatus, sequenceNumber, messageId) =>
      result = result  ++ Seq((commandStatus, messageId.map(_.asString)))
      waitingOn = waitingOn - sequenceNumber
      if (waitingOn.isEmpty) {
        forwardTo ! SendMessageAck(result)
        context.stop(self)
      }
  }
}
