package org.bingle.simulated.simulator

import org.bingle.certs.BingleCertCreator
import org.bingle.command.TextMessageCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.Engine
import org.bingle.interfaces.IChainAccess
import org.bingle.util.DateUtil.isoDate
import org.bingle.util.logDebug
import org.bingle.util.logWarn
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class EngineWrapper(val simulator: Simulator, val node: Simulator.Node) {

    val simulatedEngine: Engine
    val receiverThread: Thread
    var nextIdIndex = 0
    var chainAccess : IChainAccess
    val expectedMessages = mutableListOf<String>()

    init {
        val simulatedConfig = SimulatedCommsConfig(simulator, node)
        simulatedConfig.onMessage = {
            if(expectedMessages.contains(it.text)) {
                logDebug("EngineWrapper::onMessage handler ${it} (expected)")
                expectedMessages.remove(it.text)
            }
            else {
                logWarn("EngineWrapper::onMessage handler ${it} (unexpected)")
            }
        }
        simulatedEngine = Engine(emptyMap(), simulatedConfig)
        chainAccess = SimulatedChainAccess(simulatedEngine.keyProvider, node.username)
        node.algoId = chainAccess.address!!
        simulatedConfig.dtlsConnect = simulator.network.networkFor(node.id, node.algoId)

        receiverThread = Thread {
            while (true) {
                if (simulator.network.networkQueues.containsKey(node.algoId)) {
                    logDebug("EngineWrapper:: Poll node ${node.algoId} size ${simulator.network.networkQueues[node.algoId]?.size ?: "No queue"}")
                    val message = simulator.network.networkQueues[node.algoId]?.poll(10, TimeUnit.MINUTES)
                    if (null !== message) {
                        logDebug("EngineWrapper:: Poll got message ${message}")
                        // Check for connected

                        val cc = BingleCertCreator(chainAccess, node.algoId)

                        val serverCertBytes = cc.serverSigningCert
                        val certStream = ByteArrayInputStream(serverCertBytes)

                        val dtlsParameters = simulator.network.networkFor(node.id, cc.id).dtlsParameters

                        // call onCertificates
                        dtlsParameters?.onCertificates?.let {
                            it(certStream, certStream) // TODO: CA and server cert streams, we should validate both?
                        }
                        dtlsParameters?.onMessage?.let {
                            it(NetworkSourceKey(message.sender), message.verifiedId, message.data, message.data.size)
                        }
                    } else {
                        Thread.sleep(1000)
                    }
                }
            }
        }
        receiverThread.start()
    }

    fun doAction(nodeAction: Simulator.NodeAction) {
        when (nodeAction.what) {
            Simulator.ActionWhat.INIT -> {
                simulator.network.bind(node)
                simulatedEngine.init()
            }

            Simulator.ActionWhat.SEND -> {
                nextIdIndex += 1
                val sendToIds = simulator.ids().filter { it !== node.id }
                if (nextIdIndex >= sendToIds.size) nextIdIndex = 0
                val sendToId = sendToIds[nextIdIndex]
                val algoId = simulator.algoIdFromId(sendToId)!!

                val messageText = "Test message to ${sendToId}:${algoId} at ${isoDate()}"

                val targetWrapper = simulator.findEngineWrapperById(sendToId)
                targetWrapper.expectMessage(messageText)

                simulatedEngine.sendMessageToId(algoId, TextMessageCommand(messageText))
            }
        }
    }

    fun hasOutstandingMessages(): Boolean {
        return !expectedMessages.isEmpty()
    }

    private fun expectMessage(messageText: String) {
        expectedMessages.add(messageText)
    }

}
