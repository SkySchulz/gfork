/*
 * Copyright 2011-2018 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.recorder.http.ssl

import java.io.{ File, FileInputStream }
import java.nio.file.Path
import java.security.{ KeyStore, Security }
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.{ KeyManagerFactory, SSLContext, SSLEngine, X509KeyManager }

import scala.util.Failure

import io.gatling.commons.util.Io._
import io.gatling.commons.util.PathHelper._
import io.gatling.recorder.config.RecorderConfiguration

import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{ JdkSslContext, SslContextBuilder, SslProvider }

private[http] sealed trait SslServerContext {

  protected def context(alias: String): SSLContext

  def createSSLEngine(alias: String): SSLEngine = {
    val engine = context(alias).createSSLEngine
    engine.setUseClientMode(false)
    engine
  }
}

private[recorder] object SslServerContext {

  private val Algorithm = Option(Security.getProperty("ssl.KeyManagerFactory.algorithm")).getOrElse("SunX509")
  val Protocol = "TLS"

  def apply(config: RecorderConfiguration): SslServerContext = {

    import config.proxy.https._

    mode match {
      case HttpsMode.SelfSignedCertificate => SelfSignedCertificate

      case HttpsMode.ProvidedKeyStore =>
        val ksFile = new File(keyStore.path)
        val keyStoreType = keyStore.keyStoreType
        val password = keyStore.password.toCharArray
        new ProvidedKeystore(ksFile, keyStoreType, password)

      case HttpsMode.CertificateAuthority =>
        OnTheFly(certificateAuthority.certificatePath, certificateAuthority.privateKeyPath)
    }
  }

  object SelfSignedCertificate extends SslServerContext {

    private lazy val context = {
      val ssc = new SelfSignedCertificate
      SslContextBuilder
        .forServer(ssc.certificate, ssc.privateKey)
        .sslProvider(SslProvider.JDK)
        .build
        .asInstanceOf[JdkSslContext]
        .context
    }

    override def context(alias: String): SSLContext = context
  }

  class ProvidedKeystore(ksFile: File, val keyStoreType: KeyStoreType, val password: Array[Char]) extends SslServerContext {

    private lazy val context = {
      val keyStore = {
        val ks = KeyStore.getInstance(keyStoreType.toString)
        withCloseable(new FileInputStream(ksFile)) { ks.load(_, password) }
        ks
      }

      // Set up key manager factory to use our key store
      val kmf = KeyManagerFactory.getInstance(Algorithm)
      kmf.init(keyStore, password)

      // Initialize the SSLContext to work with our key managers.
      val serverContext = SSLContext.getInstance(Protocol)
      serverContext.init(kmf.getKeyManagers, null, null)

      serverContext
    }

    def context(alias: String): SSLContext = context

  }

  object OnTheFly {
    val GatlingCAKeyFile = "gatlingCA.key.pem"
    val GatlingCACrtFile = "gatlingCA.cert.pem"
  }

  case class OnTheFly(pemCrtFile: Path, pemKeyFile: Path) extends SslServerContext {

    require(pemCrtFile.isFile, s"$pemCrtFile is not a file")
    require(pemKeyFile.isFile, s"$pemKeyFile is not a file")

    private val password: Array[Char] = "gatling".toCharArray
    private val storeType = KeyStoreType.JKS.toString
    private val aliasContexts = new ConcurrentHashMap[String, SSLContext]
    private lazy val ca = SslCertUtil.getCA(pemCrtFile.inputStream, pemKeyFile.inputStream)
    private lazy val keyStore = {
      val ks = KeyStore.getInstance(storeType)
      ks.load(null, null)
      ks
    }

    def context(alias: String): SSLContext =
      aliasContexts.computeIfAbsent(alias, newAliasContext)

    private def newAliasContext(alias: String): SSLContext =
      SslCertUtil.updateKeystoreWithNewAlias(keyStore, password, alias, ca) match {
        case Failure(t) => throw t
        case _ =>
          // Set up key manager factory to use our key store
          val kmf = KeyManagerFactory.getInstance(Algorithm)
          kmf.init(keyStore, password)

          // Initialize the SSLContext to work with our key manager
          val serverContext = SSLContext.getInstance(Protocol)
          serverContext.init(Array(new KeyManagerDelegate(kmf.getKeyManagers.head.asInstanceOf[X509KeyManager], alias)), null, null)
          serverContext
      }
  }
}