package org.bingle.engine.mocks

import org.bingle.command.data.AdvertRecord
import org.bingle.interfaces.IResolver
import java.util.*

class MockResolver : IResolver {
    val idAdvertMap = mapOf(
        id1 to AdvertRecord(id1, endpoint1),
        id2 to AdvertRecord(id1, endpoint2),
        id3 to AdvertRecord(id3, relayId = idRelay),
        idRelay to AdvertRecord(idRelay, endpointRelay, amRelay = true),
    )
    override fun resolveIdToRelay(id: String): IResolver.RelayDns? {
        return idAdvertMap[idAdvertMap[id]?.relayId]?.endpoint?.let {
            IResolver.RelayDns(it, Date())
        }
    }

    override fun resolveIdToAdvertRecord(id: String): AdvertRecord? {
        return idAdvertMap[id]
    }
}