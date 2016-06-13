package akkasmpp.ssl

import java.net.InetSocketAddress
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{SSLParameters, KeyManagerFactory, SSLContext, TrustManagerFactory}

import com.typesafe.config.ConfigFactory

/**
  * Module for cargo-cult code I don't really understand!
  * Probably not needed anymore, can just use AkkaSslConfig.
  */
object SslUtil {

  import collection.JavaConversions._
  private val cf         = ConfigFactory.load()
  val PreferredProtocols = cf.getStringList("smpp.tls.enabled-protocols").toSet
  val PreferredCiphersSuites =
    cf.getStringList("smpp.tls.enabled-cipher-suites").toSet
  val VerifyHostname = cf.getBoolean("smpp.tls.verify-hostname")

  def sslContext(keyStore: KeyStore, password: String): SSLContext = {
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, password.toCharArray)
    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)
    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers,
                 trustManagerFactory.getTrustManagers,
                 new SecureRandom)
    context
  }

  def sslEngine(
      sslContext: SSLContext, remote: InetSocketAddress, client: Boolean) = {
    val engine = sslContext.createSSLEngine(
        remote.getAddress.getHostAddress, remote.getPort)
    //engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
    val params = new SSLParameters()
    if (VerifyHostname) params.setEndpointIdentificationAlgorithm("HTTPS")
    engine.setUseClientMode(client)
    val enabledCipherSuites =
      (engine.getSupportedCipherSuites.toSet intersect PreferredCiphersSuites).toArray
    val enabledProtocols =
      (engine.getSupportedProtocols.toSet intersect PreferredProtocols).toArray
    engine.setEnabledProtocols(enabledProtocols)
    engine.setEnabledCipherSuites(enabledCipherSuites)
    engine
  }
}
