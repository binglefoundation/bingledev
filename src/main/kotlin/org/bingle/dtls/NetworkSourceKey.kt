package org.unknown.comms

import java.net.InetSocketAddress

data class NetworkSourceKey(val inetSocketAddress: InetSocketAddress?,
    var relayChannel: Int? = null,
    var relayAddress: InetSocketAddress? = null) {

    constructor(relayAddress: InetSocketAddress, relayChannel: Int) : this(null, relayChannel, relayAddress)

    val isRelay: Boolean get() {
        return relayChannel != null
    }
    override fun toString() = "NetworkSourceKey(${if(isRelay) "relayChannel=$relayChannel, relayAddress=$relayAddress)" else "inetSocketAddress=$inetSocketAddress"})"
}
