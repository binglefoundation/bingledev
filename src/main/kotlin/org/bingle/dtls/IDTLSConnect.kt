package org.bingle.dtls

import org.bingle.engine.TurnRelayProtocol
import org.bingle.stun.StunProtocol
import java.net.InetSocketAddress

interface IDTLSConnect {
    var isInitialized: Boolean
    val userRelay: TurnRelayProtocol
    val stunProtocol: StunProtocol

    fun waitForStopped()
    fun send(to: NetworkSourceKey, data: ByteArray, len: Int?): Boolean
    fun connectionOpenTo(userId: String): NetworkSourceKey?
    fun sendRelayPing(relayAddress: InetSocketAddress)

    fun clear(target: NetworkSourceKey)
    fun clearAll()
    fun restart()
    fun init(dtlsParameters: DTLSParameters)
}