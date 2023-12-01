package org.bingle.going.apps.ddb

import org.bingle.command.data.AdvertRecord

open class DistributedDBClient(
    val myId: String,
    val lookupRootRelay: () -> String,
    val sendForResponse: (toId: String, message: Map<String, Any?>) -> Map<String, Any?>
) {

    var relayPlan: RelayPlan? = null
    var myDbServerId: String? = null

    open fun connect() {
        val rootRelayId = lookupRootRelay()
        if(myId==rootRelayId) return

        val epochResponse = sendForResponse(
            rootRelayId, mapOf(
                "app" to "ddb",
                "type" to "getEpoch",
                "senderId" to myId,
                "epochId" to -1,
            )
        )

        relayPlan = RelayPlan(
            RelayPlan.EpochParams(
                epochResponse["epochId"]?.toString()?.toInt() ?: -1,
                epochResponse["treeOrder"].toString().toInt(),
                epochResponse["relayIds"] as List<String>
            )
        )
        relayPlan?.let {
            myDbServerId = it.relayFor(myId)
        }
    }

    fun lookupAdvert(id: String): AdvertRecord? {
        return myDbServerId?.let {
            val lookupResponse =
                sendForResponse(
                    it,
                    mapOf("senderId" to myId, "app" to "ddb", "type" to "queryResolve", "epoch" to relayPlan!!.latestEpoch(-1), "id" to id)
                )
            if (lookupResponse["found"].toString() == "true") lookupResponse["advert"] as AdvertRecord else null
        }
    }

    fun upsertAdvert(id: String, advert: AdvertRecord) {
        myDbServerId?.let {
                sendForResponse(
                    it,
                    mapOf("app" to "ddb", "type" to "upsertResolve", "epoch" to relayPlan!!.latestEpoch(-1), "startId" to id,
                        "record" to advert)
                )
        }
    }

    fun epochChange(newEpochParams: RelayPlan.EpochParams) {
        relayPlan?.loadEpoch(newEpochParams) ?: throw RuntimeException("epochChange when not connected")
        relayPlan?.let { myDbServerId = it.relayFor(myId) }
    }
}