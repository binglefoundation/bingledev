package org.bingle.going.apps

import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.Engine
import org.bingle.interfaces.going.IApp
import org.bingle.util.logDebug
import org.bingle.util.logWarn
import java.net.InetSocketAddress

class RelayApp(val engine: Engine) : IApp {
    override val type: String
        get() = "relay"

    // TODO: typed message
    override fun onMessage(senderId: String, decodedMessage: MutableMap<String, Any?>) {
        val (host, port) = decodedMessage["senderAddress"]?.toString()?.trimStart('/')?.split(":")!!
        val senderAddress = InetSocketAddress(host, port.toInt())
        val senderNetworkSourceKey = NetworkSourceKey(senderAddress)

        when (decodedMessage["type"]) {
            "check" -> {
                logDebug("RelayApp:: sending response message to ${senderId}")
                engine.sender.sendMessageToNetwork(
                    senderNetworkSourceKey,
                    senderId,
                    RelayCommand.CheckResponse(1).withTag(decodedMessage["responseTag"] as String),
                    null
                )
            }

            "listen" -> {
                logDebug("RelayApp:: ${engine.currentEndpoint.port} got listen with ${senderAddress} ${senderId}")
                engine.relay.relayListen(senderAddress, senderId)
                engine.sender.sendMessageToNetwork(
                    senderNetworkSourceKey,
                    senderId,
                    RelayCommand.ListenResponse().withTag(decodedMessage["responseTag"] as String),
            null
                )
            }
            "call" -> {
                val calledId = decodedMessage["calledId"]!!
                val channel =
                    engine.relay.relayCall(calledId.toString(), senderAddress)
                if(channel==null) {
                    logDebug("RelayApp:: could not allocate channel for ${calledId}")
                }
                logDebug("RelayApp::onMessage on ${engine.currentEndpoint} sender = ${senderAddress} relayedChannels=${engine.relay.relayedChannels}")
                engine.sender.sendMessageToNetwork(
                    senderNetworkSourceKey,
                    senderId,
                    RelayCommand.CallResponse(decodedMessage["calledId"] as String, channel!!).withTag(decodedMessage["responseTag"] as String),
            null
                )
            }
            "callResponse" -> {
                // TODO:
                // comms.addTurnChannel(decodedMessage["calledId"]!!, decodedMessage["channel"]!!.toInt())
                // we need a state change to pick this up
            }
            "keepAlive" -> {
                engine.relay.keepAlive(senderId)
            }
            "triangleTest1" -> {
                logDebug("RelayApp:: triangleTest1 message, find relay to pass on message")
                val relay = engine.relayFinder.find()
                if(relay == null) {
                    logWarn("RelayApp:: triangleTest1 message: No relay found to pass on triangleTest1")
                }
                else {
                    engine.sendMessageToId(
                        relay.first,
                        RelayCommand.TriangleTest2(senderId, InetSocketAddress(
                            decodedMessage["checkingAddress"] as String,
                            decodedMessage["checkingPort"] as Int)
                        ).withTag(decodedMessage["responseTag"] as String),
                        null
                    )
                }
            }
            "triangleTest2" -> {
                // This will be picked up by tag handler in the original sender
                val networkSourceKey = NetworkSourceKey(
                    InetSocketAddress(decodedMessage["checkingAddress"]?.toString()!!,
                        (decodedMessage["checkingPort"] as? Int)!!))

                engine.sender.sendMessageToNetwork(networkSourceKey, decodedMessage["checkingId"]?.toString()!!,
                    RelayCommand.TriangleTest3().withTag(decodedMessage["responseTag"] as String),
                    null
                )
            }
            else ->
                logDebug("Unsupported relay message type ${decodedMessage["type"]}")
        }
    }

}