package akkasmpp.protocol.auth

import java.net.InetSocketAddress

import scala.concurrent.Future

/**
  * Define one of these to decide whether to allow a Bind request
  */
trait BindAuthenticator {

  /**
    * Decide whether to allow the bind based on request and connection info.
    *
    * @param bindRequest Information about the Bind Request
    * @param remoteAddress client side of the socket
    * @param localAddress the server side of the socket
    * @return
    */
  def allowBind(bindRequest: BindRequest,
                remoteAddress: InetSocketAddress,
                localAddress: InetSocketAddress): Future[BindResponse]
}
