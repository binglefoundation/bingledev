package org.bingle.interfaces.going

interface IApp {
    val type: String

    fun onMessage(senderId: String, decodedMessage: MutableMap<String, Any?>)
}