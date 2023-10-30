package org.unknown.comms.interfaces

interface IApp {
    val type: String

    fun onMessage(senderId: String, decodedMessage: MutableMap<String, Any?>)
}