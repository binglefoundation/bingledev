package org.unknown.comms.apps.ddb

import org.unknown.comms.command.CommsCommand
import org.unknown.comms.command.DdbCommand
import org.unknown.comms.interfaces.IApp

class DistributedDBApp(
    myId: String,
    relayPlan: RelayPlan,
    private val send: (id: String, command: Map<String, Any>) -> Boolean
) : IApp {

    private val distributedDB = DistributedDB(myId, relayPlan) {
        id, command -> send(id, command.toMap())
    }

    override val type: String = "ddb"

    override fun onMessage(senderId: String, decodedMessage: MutableMap<String, Any?>) {
        distributedDB.execute(senderId, CommsCommand.fromMap(decodedMessage))
    }
}