package org.unknown.comms.apps

import org.unknown.comms.Comms
import org.unknown.comms.interfaces.IApp

class PingApp(val comms: Comms) : IApp {
    override val type: String
        get() = "ping"

    override fun onMessage(senderId: String, decodedMessage: MutableMap<String, Any?>) {
        if (decodedMessage["type"] == "ping") {
            comms.sendMessageToId(
                decodedMessage["verifiedId"]?.toString()!!,
                mapOf("app" to "ping", "type" to "response", "senderId" to senderId)
            )
        } else if (decodedMessage["type"] == "response") {
            comms.pinger.onResponse(decodedMessage)
        }
    }
}