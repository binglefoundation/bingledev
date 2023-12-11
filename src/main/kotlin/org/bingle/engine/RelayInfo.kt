package org.bingle.engine

import java.net.InetSocketAddress

data class RelayInfo(val id: String,
                     val endpoint: InetSocketAddress? = null,
                     val isRoot: Boolean = false
)

data class PopulatedRelayInfo( val id: String, val endpoint: InetSocketAddress) {
    constructor(relayInfo: RelayInfo) :
            this(relayInfo.id, relayInfo.endpoint
                ?: throw RuntimeException("PopulatedRelayInfo::construct with no endpoint populated"))
}