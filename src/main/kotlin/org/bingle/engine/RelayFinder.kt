package org.bingle.engine

import org.bingle.command.DdbCommand
import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.interfaces.IChainAccess
import org.bingle.util.SHA256Hash
import org.bingle.util.logDebug
import java.math.BigInteger
import java.net.InetSocketAddress

// TODO: Engine has chainAccess and id
typealias RelayIdToAddress = Pair<String, InetSocketAddress>

class RelayFinder internal constructor(
    val algoSwap: IChainAccess,
    val myId: String,
    internal val engine: IEngineState
) {

    private val hasher = SHA256Hash()

    fun find(fromOffset: Int = 0): PopulatedRelayInfo? {
        engine.currentRelay?.let {
            return engine.currentRelay
        }

        logDebug("RelayFinder::find on ??? TODO {engine.currentEndpoint.port} by ${myId}")

        val wantRelayId = engine.config.alwaysRelayWithId
        val relayDetails =
            algoSwap.listRelaysWithIps().filter { it.id != myId && (wantRelayId == null || it.id == wantRelayId) }
        if (relayDetails.isEmpty()) {
            logDebug("RelayFinder::find - No relays found in blockchain (filtering out ${myId}) ${wantRelayId?.let { "only $wantRelayId" }}")
            return null
        }

        // Check root relays (not us if we are one)
        val rootRelays = relayDetails.filter { it.isRoot && it.id != myId}
        if(rootRelays.isEmpty()) {
            throw RuntimeException("RelayFinder::find - no root relays")
        }

        // Determine index
        val idHash = hasher.hashBigInteger("${myId}${fromOffset}")
        val partitionSize = partitionHashSpace(rootRelays.size)
        val rootRelayOffset = idHash.divide(partitionSize).intValueExact()

        val chosenRootRelay = rootRelays[rootRelayOffset]

        // Get epoch and size
        // TODO: retrying
        val epochResponse = engine.sender.sendToNetworkForResponse(NetworkSourceKey(chosenRootRelay.endpoint),
            chosenRootRelay.id,
            DdbCommand.GetEpoch(-1),
            null) as DdbCommand.GetEpochResponse

        // Find relays that are in current epoch and registered, ignore own id
        val activeRelays = relayDetails.filter {
            epochResponse.relayIds.contains(it.id) && it.id != myId
        }

        val unexpectedRelays = epochResponse.relayIds.filter {
            activeRelay -> activeRelay != myId && !relayDetails.any { it.id == activeRelay}
        }

        if(!unexpectedRelays.isEmpty()) {
            throw RuntimeException("RelayFinder::find unexpected relay ids ${unexpectedRelays} found")
        }

        // Select one
        val fullPartitionSize = partitionHashSpace(activeRelays.size)

        var extraOffset = 0
        while(extraOffset < activeRelays.size) {
            val nowIdHash = hasher.hashBigInteger("${myId}${fromOffset + extraOffset}")
            val useRelayOffset = nowIdHash.divide(fullPartitionSize).intValueExact()

            val chosenRelay = activeRelays[useRelayOffset]
            val chosenRelayFixedEndpoint = chosenRelay.endpoint
            if (null != chosenRelayFixedEndpoint) {
                if(checksOk(chosenRelay.id, chosenRelayFixedEndpoint)) {
                    // we have the (fixed) endpoint and can use it
                    engine.currentRelay = PopulatedRelayInfo(chosenRelay)
                    return engine.currentRelay
                }
            }

            val relayIP = engine.resolver.resolveIdToRelay(chosenRelay.id)
            if (null == relayIP || !checksOk(chosenRelay.id, relayIP.endpoint)) {
                extraOffset += 1
            } else {
                engine.currentRelay =
                    PopulatedRelayInfo(RelayInfo(chosenRelay.id, relayIP.endpoint, chosenRelay.isRoot))
                return engine.currentRelay
            }
        }

        throw RuntimeException("RelayFinder::find no relays could be resolved to IP")
    }

    private fun checksOk(id: String, endpoint: InetSocketAddress): Boolean {
        val checkResponse = engine.sender.sendToNetworkForResponse(NetworkSourceKey(endpoint), id,
            RelayCommand.Check())
        return null == checkResponse.fail
    }

    private fun partitionHashSpace(divideBy: Int): BigInteger =
        BigInteger.ONE.shiftLeft(256).divide(BigInteger.valueOf(divideBy.toLong()))
}