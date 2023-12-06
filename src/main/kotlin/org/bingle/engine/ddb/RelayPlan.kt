package org.bingle.engine.ddb

import org.bingle.util.NetDet

const val TREE_ORDER = 4

class RelayPlan() {

    constructor(epochParams: EpochParams): this() {
        loadEpoch(epochParams)
    }

    class EpochInfo(
        val netDet: NetDet,
        val idToOrdinal: Map<String, Int>
    ) {
        val ordinalToId = idToOrdinal.entries.associate {
            (k,v) ->
            v to k
        }
    }

    data class EpochParams(val epochId: Int, val treeOrder: Int, val relayIds: List<String>)

    val epochGraphs = mutableMapOf<Int, EpochInfo>()

    fun bootstrap(myId: String) {
        val netDetInitial = NetDet(1, TREE_ORDER)
        netDetInitial.fill()
        epochGraphs[0] = EpochInfo(netDetInitial, mapOf(myId to 0))
    }

    fun addEpoch(newId: String) {
        val latestEpochId = latestEpoch(-1)
        val latestEpochInfo = epochGraphs[latestEpochId] ?: throw RuntimeException("No latest epoch")
        if(latestEpochInfo.idToOrdinal.containsKey(newId)) {
            throw RuntimeException("Adding ${newId} which is already in the latest epoch")
        }

        val newOrdinal = latestEpochInfo.netDet.numberNodes
        val netDetNew = NetDet(latestEpochInfo.netDet.numberNodes + 1, TREE_ORDER)
        netDetNew.fill()
        val newIdToOrdinal = latestEpochInfo.idToOrdinal.toMutableMap()
        newIdToOrdinal[newId] = newOrdinal
        epochGraphs[latestEpochId + 1] = EpochInfo(netDetNew, newIdToOrdinal)
    }

    /**
     * For any `epoch`, assuming we have on node `startId`
     * and have a message originating from `myId`
     * return the nodes to send that message to
     *
     * @param epoch the epoch number, determines the number of nodes, -1 implies the current epoch
     * @param myId the id of the current relaying node
     * @param startId the id of the message originator
     * @return the set of nodes to foward the message to
     */
    fun next(epoch: Int, myId: String, startId: String): Set<String> {
        val (_, epochInfo) = getEpochInfo(epoch)
        val start = epochInfo.idToOrdinal[startId] ?:
            throw RuntimeException("startId ${startId} not found")
        val forOrdinal = epochInfo.idToOrdinal[myId] ?: throw RuntimeException("node id ${myId} not found")

        return epochInfo.netDet.flood(start, forOrdinal).map {
            epochInfo.ordinalToId[it] ?: throw RuntimeException("node id ${it} not found")
        }.toSet()
    }

    fun getEpochParams(epoch: Int): EpochParams {
        val (epochId, epochInfo) = getEpochInfo(epoch)
        return EpochParams(epochId, epochInfo.netDet.treeOrder, epochInfo.idToOrdinal.keys.toSortedSet().toList())
    }

    fun latestEpoch(epoch: Int) = if (epoch >= 0) epoch else epochGraphs.keys.maxOrNull() ?: 0

    fun loadEpoch(epochParams: EpochParams) {
        latestEpoch(-1).let {
            epochGraphs[it] = EpochInfo(NetDet(epochParams.relayIds.size, epochParams.treeOrder), epochParams.relayIds.mapIndexed {index, id -> Pair(id, index) }.toMap())
        }
    }

    fun relayFor(id: String): String? {
        val (_, epochInfo) = getEpochInfo(-1)
        val nodeIdBefore = epochInfo.idToOrdinal.keys.zipWithNext().find { it.first > id && it.second < id }
        if(null == nodeIdBefore) {
            if(epochInfo.idToOrdinal.size == 1 || epochInfo.idToOrdinal.keys.first() > id) return epochInfo.idToOrdinal.keys.first()
            return epochInfo.idToOrdinal.keys.last()
        }

        return null
    }

    private fun getEpochInfo(epoch: Int): Pair<Int, EpochInfo> {
        val useEpoch = latestEpoch(epoch)
        return epochGraphs[useEpoch]?.let { Pair(useEpoch, it) } ?: throw RuntimeException("Epoch ${useEpoch} not loaded")
    }


}