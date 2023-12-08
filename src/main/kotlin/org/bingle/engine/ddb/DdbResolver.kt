package org.bingle.engine.ddb

import org.bingle.command.DdbCommand
import org.bingle.command.data.AdvertRecord
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.IEngineState
import org.bingle.interfaces.IResolver
import org.bingle.util.logWarn

class DdbResolver(val engineState: IEngineState) : IResolver {
    override fun resolveIdToRelay(id: String): IResolver.RelayDns? {
        // Do we want to lookup in blockchain first?
        val relayAdvertRecord = resolveIdToAdvertRecord(id)
        return relayAdvertRecord?.let { ad -> ad.endpoint?.let { IResolver.RelayDns(it, ad.date) } }
    }

    override fun resolveIdToAdvertRecord(id: String): AdvertRecord? {
        // TODO: this probably wants to retry
        val myCurrentRelay = engineState.relayFinder.find()
            ?: throw RuntimeException("DdbResolver::resolveIdToAdvertRecord with no relays found")

        val queryResponse = engineState.sender.sendToNetworkForResponse(
            NetworkSourceKey(myCurrentRelay.second),
            myCurrentRelay.first,
            DdbCommand.QueryResolve(id),
            null
        )
        if (queryResponse.fail != null) {
            logWarn("DdbResolver::resolveIdToAdvertRecord query failed with ${queryResponse.fail}")
            return null
        }

        return (queryResponse as DdbCommand.QueryResponse).advert
    }
}