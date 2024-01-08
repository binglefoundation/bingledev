package org.bingle.simulated.simulator

import org.bingle.dtls.DTLSParameters
import org.bingle.dtls.IDTLSConnect
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.TurnRelayProtocol
import org.bingle.stun.StunProtocol
import java.net.InetSocketAddress

class SimulatedNetworkNode(val network: SimulatedNetwork, val myId: String, val algoId: String) : IDTLSConnect {
    var dtlsParameters: DTLSParameters? = null

    override var isInitialized: Boolean = false
    override val userRelay: TurnRelayProtocol
        get() = TODO("Not yet implemented")
    override val stunProtocol: StunProtocol
        get() = TODO("Not yet implemented")

    override fun waitForStopped() {
        TODO("Not yet implemented")
    }

    override fun send(to: NetworkSourceKey, data: ByteArray, len: Int?): Boolean {
        network.sendTo(network.addressFor(algoId), algoId, to.inetSocketAddress!!, data)
        return true
    }

    override fun connectionOpenTo(userId: String): NetworkSourceKey? {
        return NetworkSourceKey(network.addressFor(userId))
    }

    override fun sendRelayPing(relayAddress: InetSocketAddress) {
        TODO("Not yet implemented")
    }

    override fun clear(target: NetworkSourceKey) {
        TODO("Not yet implemented")
    }

    override fun clearAll() {
        TODO("Not yet implemented")
    }

    override fun restart() {
        TODO("Not yet implemented")
    }

    override fun init(dtlsParameters: DTLSParameters) {
        this.dtlsParameters = dtlsParameters
    }

}
