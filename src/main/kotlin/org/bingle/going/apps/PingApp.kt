package org.bingle.going.apps

import org.bingle.command.Ping
import org.bingle.engine.Engine
import org.bingle.interfaces.going.IApp

class PingApp(val engine: Engine) : IApp {
    override val type: String
        get() = "ping"

    override fun onMessage(senderId: String, decodedMessage: MutableMap<String, Any?>) {
        if (decodedMessage["type"] == "ping") {
            engine.sendMessageToId(
                decodedMessage["verifiedId"]?.toString()!!,
                Ping.Response(),
            )
        } else if (decodedMessage["type"] == "response") {
            engine.pinger.onResponse(decodedMessage)
        }
    }
}