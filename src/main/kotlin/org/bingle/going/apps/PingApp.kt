package org.bingle.going.apps

import org.bingle.engine.Engine
import org.bingle.interfaces.going.IApp

class PingApp(val engine: Engine) : IApp {
    override val type: String
        get() = "ping"

    override fun onMessage(senderId: String, decodedMessage: MutableMap<String, Any?>) {
        if (decodedMessage["type"] == "ping") {
            engine.sendMessageToId(
                decodedMessage["verifiedId"]?.toString()!!,
                mapOf("app" to "ping", "type" to "response", "senderId" to senderId)
            )
        } else if (decodedMessage["type"] == "response") {
            engine.pinger.onResponse(decodedMessage)
        }
    }
}