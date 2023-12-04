package org.bingle.engine

import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.interfaces.IChainAccess
import org.bingle.interfaces.IResolver
import org.bingle.util.logDebug
import java.net.InetSocketAddress
import java.time.temporal.ChronoUnit
import java.util.*

// TODO: Engine has chainAccess and id
typealias RelayIdToAddress = Pair<String, InetSocketAddress>

class RelayFinder internal constructor(
    val algoSwap: IChainAccess,
    val myId: String,
    internal val engine: IEngineState
) {

    fun find(): RelayIdToAddress? {
        logDebug("RelayFinder::find on ${engine.currentEndpoint.port} by ${myId}")
        // TODO: keep track of relay state

        val wantRelayId = engine.config.alwaysRelayWithId
        val relays =
            algoSwap.listRelaysWithIps().filter { it.first != myId && (wantRelayId == null || it.first == wantRelayId) }
        if (relays.isEmpty()) {
            logDebug("RelayFinder::find - No relays found in blockchain (filtering out ${myId}) ${wantRelayId?.let { "only $wantRelayId" }}")
            return null
        }

        val relaysWithIps = relays.mapNotNull { relay ->
            val relayIP = relay.second
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
                NetworkSourceKey(it.second.endpoint), it.first,
                RelayCommand.Check(),
                engine.config.timeouts.relayCheck ?: 15000
            )
            logDebug("RelayFinder::find - sendToIdForResponse returns ${response}")
            null == response.fail
        }

        logDebug("RelayFinder::find selected relay ${relayEntry ?: "<none>"}")
        return relayEntry?.let { Pair(it.first, it.second.endpoint) }
    }
}