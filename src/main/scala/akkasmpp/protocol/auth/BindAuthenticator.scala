package akkasmpp.protocol.auth

import scala.concurrent.Future

/**
 * Define one of these to decide whether to allow a Bind request
 */
trait BindAuthenticator {

  def allowBind(bindRequest: BindRequest): Future[BindResponse]

}
