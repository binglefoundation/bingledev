package org.bingle.simulated.simulator

import org.bingle.interfaces.IChainAccess
import org.bingle.util.logDebug
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue

class SimulatedNetwork() {
    data class Packet(
        val sender: InetSocketAddress,
        val verifiedId: String,
        val data: ByteArray
    )

    val networkNodes = mutableMapOf<String, SimulatedNetworkNode>()
    val addressedNodeIds = mutableMapOf<InetSocketAddress, String>()
    val networkQueues = mutableMapOf<String, LinkedBlockingQueue<SimulatedNetwork.Packet>>()

    fun networkFor(id: String, algoId: String): SimulatedNetworkNode {
        return networkNodes.computeIfAbsent(id) { SimulatedNetworkNode(this, id, algoId) }
    }

    fun bind(node: Simulator.Node) {
        val addressBytes = ByteArray(4)
        addressBytes[0] = 2
        addressBytes[1] = (addressedNodeIds.size shr 16).toByte()
        addressBytes[2] = ((addressedNodeIds.size shr 8) and 255).toByte()
        addressBytes[3] = (addressedNodeIds.size and 255).toByte()

        val address = InetSocketAddress(InetAddress.getByAddress(addressBytes), 7777)
        addressedNodeIds[address] = node.algoId
    }

    fun sendTo(sender: InetSocketAddress, senderId: String, dest: InetSocketAddress, message: ByteArray) {
        val toNodeId = addressedNodeIds[dest]
        if(toNodeId !== null) {
            if(!networkQueues.containsKey(toNodeId)) {
                networkQueues.put(toNodeId, LinkedBlockingQueue())
            }
            networkQueues[toNodeId]?.add(Packet(sender, senderId, message))
            logDebug("SimulatedNetwork::sendTo send to ${toNodeId}")
        }
    }

    fun addressFor(algoId: String) : InetSocketAddress {
        return addressedNodeIds.filterValues { it === algoId }.keys.firstOrNull() ?:
            throw RuntimeException("${algoId} is not bound")
    }
}
