package org.bingle.engine.ddb

import org.bingle.command.DdbCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.IEngineState
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DdbInitialize(private val engine: IEngineState) {
    fun becomeRelay(endpoint: InetSocketAddress) {
        val peerRelay = engine.relayFinder.find()
        val relayPlan = if (peerRelay == null) {
            val res = RelayPlan()
            res.bootstrap(engine.id)
            res
        } else {
            // Initialize relay plan from peer epoch params
            val epochResponse = engine.sender.sendToNetworkForResponse(
                NetworkSourceKey(peerRelay.endpoint),
                peerRelay.id,
                DdbCommand.GetEpoch(-1),
                null) as DdbCommand.GetEpochResponse
            RelayPlan(RelayPlan.EpochParams(epochResponse.epochId, epochResponse.treeOrder, epochResponse.relayIds))
        }

        engine.distributedDB = DistributedDB(engine.id, relayPlan)

        if(peerRelay != null) {
            engine.ddbWaitingForLoadLatch = CountDownLatch(1)

            // initialize and sign on
            val initResponse = engine.sender.sendToNetworkForResponse(
                NetworkSourceKey(peerRelay.endpoint),
                peerRelay.id,
                DdbCommand.InitResolve()
            ) as DdbCommand.InitResponse
            if(initResponse.fail != null) {
                throw RuntimeException("DdbInitialize::becomeRelay initReponse failed with ${initResponse.fail}")
            }
            engine.distributedDB.expectedRecords = initResponse.dbCount

            // we get called back on completion
            // TODO: config timeout
            val waitRes = engine.ddbWaitingForLoadLatch?.await(120000, TimeUnit.MILLISECONDS) ?: false
            if(!waitRes) {
                throw RuntimeException("DdbInitialize::becomeRelay load timed out")
            }
            // our DDB should now be complete and we are signed on
        }

        if (engine.config.registerIP) {
            // e.g. we are a root relay
            engine.chainAccess.registerIP(engine.id, endpoint)
        }
    }
}