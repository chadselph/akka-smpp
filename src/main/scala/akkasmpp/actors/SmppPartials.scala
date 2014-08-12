package akkasmpp.actors

import akka.pattern.pipe
import akka.actor.ActorLogging
import akkasmpp.protocol.{CommandStatus, GenericNack, Pdu, BindLike, BindRespLike, EnquireLinkResp, EnquireLink}
import scala.concurrent.{ExecutionContext, Future}

trait SmppPartials extends SmppActor with ActorLogging {

  val wire: SmppServerHandler.SmppPipeLine
  implicit val ec: ExecutionContext = context.dispatcher

  def bind(cb: (BindLike) => BindRespLike): Receive = {
    case wire.Event(b: BindLike) => sender ! wire.Command(cb(b))
  }

  def bindF(cb: (BindLike) => Future[BindRespLike]): Receive = {
    case wire.Event(b: BindLike) => cb(b).map(wire.Command(_)) pipeTo sender
  }

  def enquireLinkResponder: Receive = {
    case wire.Event(s @ EnquireLink(seq)) =>
      log.debug(s"Got enquire_link $s")
      sender ! wire.Command(EnquireLinkResp(seq))
  }

  def genericNack: Receive = {
    case wire.Event(p: Pdu) => GenericNack(CommandStatus.ESME_RINVCMDID, p.sequenceNumber)
  }

}
