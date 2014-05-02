package akkasmpp.ssl

import javax.net.ssl.{SSLEngine, TrustManagerFactory, KeyManagerFactory, SSLContext}
import java.security.{SecureRandom, KeyStore}
import java.net.InetSocketAddress

/**
 * Module for cargo-cult code I don't really understand!
 */
object SslUtil {

  def sslContext(keyStore: KeyStore, password: String): SSLContext = {
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, password.toCharArray)
    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)
    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }

  def sslEngine(sslContext: SSLContext, remote: InetSocketAddress, client: Boolean) = {
    val engine = sslContext.createSSLEngine(remote.getAddress.getHostAddress, remote.getPort)
    //engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
    engine.setUseClientMode(client)
    engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA"))
    engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
    engine
  }
}
