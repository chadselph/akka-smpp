package akkasmpp.actors

import akka.pattern.pipe
import akka.actor.ActorLogging
import akkasmpp.protocol.{CommandStatus, GenericNack, Pdu, BindLike, BindRespLike, EnquireLinkResp, EnquireLink}
import scala.concurrent.{ExecutionContext, Future}

trait SmppPartials extends SmppActor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher

  def bind(cb: (BindLike) => BindRespLike): Receive = {
    case (b: BindLike) => sender ! cb(b)
  }

  def bindF(cb: (BindLike) => Future[BindRespLike]): Receive = {
    case (b: BindLike) => cb(b) pipeTo sender
  }

  def enquireLinkResponder: Receive = {
    case s @ EnquireLink(seq) =>
      log.debug(s"Got enquire_link $s")
      sender ! EnquireLinkResp(seq)
  }

  def genericNack: Receive = {
    case p: Pdu => GenericNack(CommandStatus.ESME_RINVCMDID, p.sequenceNumber)
  }

}
