package org.bingle.engine

import org.bingle.dtls.NetworkSourceKey
import org.bingle.interfaces.IChainAccess
import org.bingle.interfaces.IResolver
import org.bingle.util.logDebug
import java.net.InetSocketAddress
import java.time.temporal.ChronoUnit
import java.util.*

// TODO: Engine has chainAccess and id
typealias RelayIdToAddress = Pair<String, InetSocketAddress>

class RelayFinder {

    val algoSwap: IChainAccess
    val myId: String
    internal val engine: IEngineState

    internal constructor(algoSwap: IChainAccess, myId: String, engine: IEngineState) {
        this.algoSwap = algoSwap
        this.myId = myId
        this.engine = engine
    }

    fun find(): RelayIdToAddress? {
        logDebug("RelayFinder::find on ${engine.currentEndpoint?.port} by ${myId}")
        // TODO: keep track of relay state

        val wantRelayId = engine.config.alwaysRelayWithId
        val relays =
            algoSwap.listRelaysWithIps().filter { it.first != myId && (wantRelayId == null || it.first == wantRelayId) }
        if (relays.isEmpty()) {
            logDebug("RelayFinder::find - No relays found in blockchain (filtering out ${myId}) ${wantRelayId?.let { "only $wantRelayId" }}")
            return null
        }

        val relaysWithIps = relays.mapNotNull { relay ->
            var relayIP = relay.second
            val relayWithIP = if (null == relayIP) {
                val res = engine.nameResolver.resolveIdToRelay(relay.first)
                logDebug("RelayFinder::find - DNS lookup for relay ${relay} returns ${relayIP}")
                res
            } else IResolver.RelayDns(relayIP, Date.from(Date().toInstant().plus(1, ChronoUnit.DAYS)))

            relayWithIP?.let { Pair(relay.first, relayWithIP) }
        }
            .sortedBy { it.second.updated }
            .reversed()

        val relayEntry = relaysWithIps.find {
            logDebug("RelayFinder - send check for ${myId} to ${it}")
            val response = engine.sender.sendToNetworkForResponse(
                NetworkSourceKey(it.second.endpoint), it.first, mapOf("app" to "relay", "type" to "check"),
                engine.config.timeouts.relayCheck ?: 15000
            )
            logDebug("RelayFinder::find - sendToIdForResponse returns ${response}")
            response["fail"] == null
        }

        logDebug("RelayFinder::find selected relay ${relayEntry ?: "<none>"}")
        return relayEntry?.let { Pair(it.first, it.second.endpoint) }
    }
}