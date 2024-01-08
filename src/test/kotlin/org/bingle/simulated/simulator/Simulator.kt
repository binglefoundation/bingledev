package org.bingle.simulated.simulator

import org.bingle.util.logDebug
import java.lang.Thread.sleep
import java.util.*

class Simulator {
    enum class RelayType {
        ROOT_RELAY, RELAY, NOT_RELAY
    }

    enum class ActionWhat {
        INIT,
        SEND,
    }

    data class Action(
        var afterSeconds: Int? = null,
        var everySeconds: Int? = null
    ) {
        lateinit var what: ActionWhat
    }

    data class Node(
        val id: String,
        val username: String,
    ) {
        lateinit var algoId: String
        lateinit var relayType: RelayType
        val actions = mutableListOf<Action>()
    }

    data class NodeAction(
        val nodeId: String,
        val nextSeconds: Int?,
        val what: ActionWhat
    )

    private lateinit var engineWrappersById: Map<String, EngineWrapper>
    private lateinit var engineWrappers: List<EngineWrapper>
    private val nodes = mutableListOf<Node>()
    val network = SimulatedNetwork()

    fun node(id: String, username: String): Simulator {
        nodes.add(Node(id, username))
        return this
    }

    fun relay(asRelayType: RelayType): Simulator {
        nodes.last().relayType = asRelayType
        return this
    }

    fun after(seconds: Int): Simulator {
        nodes.last().actions.add(Action(afterSeconds = seconds))
        return this
    }

    fun every(seconds: Int): Simulator {
        nodes.last().actions.add(Action(everySeconds = seconds))
        return this
    }

    fun immediately(): Simulator {
        nodes.last().actions.add(Action(afterSeconds = 0))
        return this
    }

    fun init(): Simulator {
        nodes.last().actions.last().what = ActionWhat.INIT
        return this
    }

    fun send(): Simulator {
        nodes.last().actions.last().what = ActionWhat.SEND
        return this
    }


    fun runUntilFinished() {
        engineWrappers = nodes.map {
            EngineWrapper(this, it)
        }

        engineWrappersById = engineWrappers.associateBy { it.node.id }

        val actionsWithTimes = nodes.flatMap { node ->
            node.actions.map {
                Pair(
                    it.afterSeconds ?: it.everySeconds ?: 0,
                    NodeAction(node.id, it.everySeconds, it.what)
                )
            }
        }

        val actionsByTime = TreeMap(actionsWithTimes.groupBy { it.first }
            .mapValues { actionTimeEntry -> actionTimeEntry.value.map { it.second } })


        while (!actionsByTime.isEmpty()) {
            val now = actionsByTime.keys.first()
            Thread.sleep(now * 1000L)

            val (nextTime, nodeActions) = actionsByTime.firstEntry()
            nodeActions.forEach { nodeAction ->
                logDebug("Run action ${nodeAction}")
                val nodeWrapper = engineWrappersById[nodeAction.nodeId]
                    ?: throw RuntimeException("Simulator::runUntilFinished ${nodeAction.nodeId} not found")

                nodeWrapper.doAction(nodeAction)

                if (null != nodeAction.nextSeconds) {
                    // Possibly throw here as we will run forever
                    val nextTime = now + nodeAction.nextSeconds
                    val nextSlot = actionsByTime[nextTime]
                    if (null == nextSlot) {
                        actionsByTime[nextTime] =
                            actionsByTime[nextTime]?.let { it + listOf(nodeAction) } ?: listOf(nodeAction)
                    }
                }
                else {
                    actionsByTime[nextTime] = actionsByTime[nextTime]?.filter { it !== nodeAction }
                    if(actionsByTime[nextTime]?.isEmpty() ?: false) actionsByTime.remove(nextTime)
                }
            }
        }

        while(engineWrappers.any { it.hasOutstandingMessages() }) {
            sleep(2000)
        }
    }

    fun ids(): List<String> {
        return nodes.map { it.id }
    }

    fun findEngineWrapperById(nodeId: String): EngineWrapper {
        return engineWrappersById[nodeId] ?:
            throw RuntimeException("Simulator::findEngineWrapperById ${nodeId} not found")
    }

    fun algoIdFromId(id: String): String? {
        return nodes.find { it.id === id }?.algoId
    }
}
