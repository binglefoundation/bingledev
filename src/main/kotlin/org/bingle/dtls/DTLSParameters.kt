package com.creatotronik.dtls

import com.creatotronik.ssledtls.IPacketProtocol
import com.creatotronik.stun.StunResponse
import org.unknown.comms.NetworkSourceKey
import java.io.ByteArrayInputStream
import java.io.InputStream

open class DTLSParameters(
    val port: Int = 0,
    val onMessage: (fromAddress: NetworkSourceKey, fromVerifiedId: String?, messageBuffer: ByteArray, messageLength: Int) -> Unit,
    val resources: IResourceUtil,
    val makeProtocolHandler: (() -> IPacketProtocol)? = null,
    val onStunResponse: ((response: StunResponse) -> Unit)? = null,
    val stunServers: List<String>? = null,
    var serverEncryptionCert: ByteArray,
    var serverEncryptionKey: ByteArray,
    var serverSigningCert: ByteArray,
    var serverSigningKey: ByteArray,
    var clientCert: ByteArray,
    var clientKey: ByteArray,
    var caCert: ByteArray,
    var disableListener: Boolean = false,
    val onCertificates: ((cert: InputStream, caCert: InputStream) -> String)? = null,
    val handshakeTimeoutMillis: Int? = null,
    val dtlsPacketReceiveTimeout: Int? = null, // controls delay on respond to Done
    val verifyTimeout: Int? = null,
) {
    val serverEncryptionCertStream get() = ByteArrayInputStream(serverEncryptionCert)
    val serverEncryptionKeyStream get() = ByteArrayInputStream(serverEncryptionKey)
    val serverSigningCertStream get() = ByteArrayInputStream(serverSigningCert)
    val serverSigningKeyStream get() = ByteArrayInputStream(serverSigningKey)
    val clientCertStream get() = ByteArrayInputStream(clientCert)
    val clientKeyStream get() = ByteArrayInputStream(clientKey)
    val caCertStream get() = ByteArrayInputStream(caCert)
}