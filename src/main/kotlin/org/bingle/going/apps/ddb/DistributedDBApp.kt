package org.bingle.going.apps.ddb

import org.bingle.command.BaseCommand
import org.bingle.command.Ddb
import org.bingle.going.apps.ddb.DistributedDB
import org.bingle.interfaces.going.IApp

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
        distributedDB.execute(senderId, BaseCommand.fromMap<Ddb>(decodedMessage))
    }
}