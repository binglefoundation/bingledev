package org.bingle.engine.ddb

import org.bingle.command.DdbCommand
import org.bingle.command.data.AdvertRecord
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.IEngineState
import org.bingle.interfaces.IAdvertiser
import org.bingle.interfaces.IKeyProvider
import org.bingle.util.logWarn
import java.net.InetSocketAddress

class DdbAdvertiser(private val engineState: IEngineState) : IAdvertiser {
    override fun advertise(keyProvider: IKeyProvider, endpoint: InetSocketAddress) {
        val myId = fetchValidMyId(keyProvider)
        val advertRecord = AdvertRecord(myId, endpoint)

        updateRecordToRelay(advertRecord, myId)
    }

    override fun advertiseUsingRelay(keyProvider: IKeyProvider, relayId: String) {
        val myId = fetchValidMyId(keyProvider)
        val advertRecord = AdvertRecord(myId, relayId=relayId)

        updateRecordToRelay(advertRecord, myId)
    }

    override fun advertiseAmRelay(
        keyProvider: IKeyProvider,
        endpoint: InetSocketAddress,
        relayEndpoint: InetSocketAddress
    ) {
        val myId = fetchValidMyId(keyProvider)
        val advertRecord = AdvertRecord(myId, endpoint=relayEndpoint, amRelay = true)

        updateRecordToRelay(advertRecord, myId)
    }

    private fun fetchValidMyId(keyProvider: IKeyProvider): String =
        keyProvider.getId() ?: throw RuntimeException("DdbAdvertiser::resolveIdToAdvertRecord with no user id")

    private fun updateRecordToRelay(advertRecord: AdvertRecord, myId: String) {
        // TODO: sign
        val myCurrentRelay = engineState.relayFinder.find()
            ?: throw RuntimeException("DdbAdvertiser::resolveIdToAdvertRecord with no relays found")

        // TODO retries
        val queryResponse = engineState.sender.sendToNetworkForResponse(
            NetworkSourceKey(myCurrentRelay.endpoint),
            myCurrentRelay.id,
            DdbCommand.UpsertResolve(advertRecord, myId),
            null
        )
        if (queryResponse.fail != null) {
            logWarn("DdbResolver::resolveIdToAdvertRecord query failed with ${queryResponse.fail}")
            return
        }
    }
}