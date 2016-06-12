package akkasmpp.ssl

import javax.net.ssl.{SSLContext, SSLParameters}

import akka.stream.TLSClientAuth
import akka.stream.TLSProtocol.NegotiateNewSession
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import scala.collection.immutable

/**
  * Stolen from akka http. Would have been nice
  * if there was a generic one in akka proper...
  *
  * Basically just carries your java SSL classes
  * into the akka-streams API.
  */
case class TlsContext(
    sslContext: SSLContext,
    sslConfig: Option[AkkaSSLConfig] = None,
    enabledCipherSuites: Option[immutable.Seq[String]] = None,
    enabledProtocols: Option[immutable.Seq[String]] = None,
    clientAuth: Option[TLSClientAuth] = None,
    sslParameters: Option[SSLParameters] = None) {

  def firstSession =
    NegotiateNewSession(
        enabledCipherSuites, enabledProtocols, clientAuth, sslParameters)
}
