package org.bingle.engine

import org.bingle.command.BaseCommand.Companion.klaxonParser
import org.bingle.command.data.AdvertRecord
import org.bingle.dtls.NetworkSourceKey
import org.bingle.interfaces.IChainAccess
import org.bingle.interfaces.SendProgress
import org.bingle.util.logDebug
import org.bingle.util.logWarn
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.TimeUnit

class Sender {
    private val engine: IEngineState

    internal constructor(engine: IEngineState) {
        this.engine = engine
    }

    fun sendMessage(
        username: String,
        message: Map<String, Any?>,
        progress: (p: SendProgress, id: String?) -> Unit
    ): Boolean {
        logDebug("Sender::sendMessage ${username} <= ${message}")

        val userId = try {
            progress.invoke(SendProgress.LOOKUP, null)
            val userId =
                engine.chainAccess.retrying<String?, IChainAccess> { engine.chainAccess.findIdByUsername(username) }
            if (null == userId) {
                println("Sender::sendMessage username ${username} not registered in blockchain")
                return false
            }
            logDebug("Sender::SendMessage Found user ${username} => ${userId}")
            userId
        } catch (ex: Exception) {
            System.err.println("Sender::sendMessage threw $ex")
            ex.printStackTrace(System.err)

            return false
        }

        return sendMessageToId(userId, message, progress)
    }

    fun sendMessageToId(
        userId: String,
        message: Map<String, Any?>,
        progress: ((p: SendProgress, id: String?) -> Unit)?
    ): Boolean {
        logDebug("Sender::sendMessageToId ${userId} <= ${message}")

        try {
            // If we have a channel open, don't resolve or use a relay
            // but also we need to deal with the case where we received a connection via
            // an inbound relay channel?
            var networkSourceKey = engine.config.dtlsConnect.connectionOpenTo(userId)

            if (networkSourceKey == null) {
                progress?.invoke(SendProgress.RESOLVE, null)

                val advertRecord = advertRecordForId(userId)
                if (advertRecord == null) {
                    progress?.invoke(SendProgress.FAILED, null)
                    return false
                }

                progress?.invoke(SendProgress.SENDING, null)

                networkSourceKey = if (advertRecord.endpoint == null && advertRecord.relayId != null) {
                    val relayAdvertRecord = engine.nameResolver.resolveIdToAdvertRecord(advertRecord.relayId)
                    if (relayAdvertRecord?.amRelay != true) {
                        logWarn("Sender::sendMessageToId: ${userId} => ${advertRecord.relayId} which is not a relay: $relayAdvertRecord}")
                        progress?.invoke(SendProgress.FAILED, advertRecord.relayId)
                        return false
                    }

                    if (engine.listening && engine.config.localToLoopback == true) {
                        throw RuntimeException("Trying to change to local and no current endpoint to check against")
                    }
                    val relayEndpoint =
                        if (engine.config.localToLoopback == true && relayAdvertRecord.endpoint?.address == engine.currentEndpoint.address) {
                            logDebug("Sender::sendMessageToId - Local relay endpoint, changed to localhost")
                            InetSocketAddress("127.0.0.1", relayAdvertRecord.endpoint?.port as Int)
                        } else {
                            relayAdvertRecord.endpoint!!
                        }

                    val relayResponse = sendToIdForResponse(
                        advertRecord.relayId,
                        mapOf(
                            "app" to "relay",
                            "type" to "call",
                            "calledId" to userId
                        ),
                        null
                    )
                    if (relayResponse.containsKey("fail")) {
                        logWarn("Sender::sendMessageToId: ${userId} => ${advertRecord.relayId} failed with ${relayResponse}")
                        progress?.invoke(SendProgress.FAILED, advertRecord.relayId)
                        return false
                    }

                    val relayChannel = relayResponse["channel"] as Int?
                    if (relayChannel == null) {
                        logWarn("Sender::sendMessageToId: ${userId} => ${advertRecord.relayId} returns no channel ${relayResponse}")
                        progress?.invoke(SendProgress.FAILED, advertRecord.relayId)
                        return false
                    }

                    val nsk = NetworkSourceKey(relayEndpoint, relayChannel)
                    logDebug("Sender::sendMessageToId: ${userId} via relay ${nsk}")
                    nsk
                } else {
                    if (engine.listening && (engine.config.localToLoopback == true)) {
                        throw RuntimeException("Trying to change to local and no current endpoint to check against")
                    }

                    val directEndpoint =
                        if (engine.config.localToLoopback == true && advertRecord.endpoint?.address == engine.currentEndpoint.address) {
                            logDebug("Sender::sendMessageToId - direct endpoint, changed to localhost")
                            InetSocketAddress("127.0.0.1", advertRecord.endpoint?.port as Int)
                        } else {
                            advertRecord.endpoint
                        }

                    NetworkSourceKey(directEndpoint)
                }
            } else {
                logDebug("Sender::sendMessageToId ${userId} connection open as ${networkSourceKey}")
            }

            val res = sendMessageToNetwork(networkSourceKey, userId, message, progress)

            logDebug("Sender::sendMessageToId ${userId} <= ${message} res=${res}")

            return res
        } catch (ex: Exception) {
            System.err.println("Sender::sendMessageToId threw $ex")
            ex.printStackTrace(System.err)

            return false
        }
    }

    fun sendToIdForResponse(userId: String, message: Map<String, Any?>, timeoutMs: Long?): Map<String, Any?> =
        sendForResponse(message, timeoutMs) {
            sendMessageToId(userId, it, null)
        }

    // TODO: wrap inside WithResponse class
    private fun sendForResponse(
        message: Map<String, Any?>,
        timeoutMs: Long?,
        sender: (Map<String, Any?>) -> Boolean
    ): Map<String, Any?> {
        val tag = UUID.randomUUID().toString()
        val sendingMessage = message.toMutableMap()
        sendingMessage["responseTag"] = tag
        engine.responseSlots[tag] = ResponseSlot()

        if (!sender(sendingMessage)) {
            logWarn("Sender: ${engine.config.port} sendForResponse send failed")
            return mapOf("fail" to "sendFail")
        }

        logDebug("Sender: ${engine.config.port} sendForResponse - wait for ${timeoutMs} ms")
        val awaitRes = if (timeoutMs == null) {
            engine.responseSlots[tag]?.latch?.await()
            true
        } else {
            engine.responseSlots[tag]?.latch?.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        if (awaitRes == false) {
            logDebug("Sender: ${engine.config.port} sendForResponse times out")
            return mapOf("fail" to "timeout")
        }

        logDebug("Sender: ${engine.config.port} sendForResponse got response for ${tag} - ${engine.responseSlots[tag]}")
        val res = engine.responseSlots[tag]?.msg
        engine.responseSlots.remove(tag)
        return res ?: mapOf("fail" to "no message in slot")
    }

    internal fun sendMessageToNetwork(
        networkSourceKey: NetworkSourceKey,
        userId: String,
        message: Map<String, Any?>,
        progress: ((p: SendProgress, id: String?) -> Unit)?
    ): Boolean {
        // TODO: rationalize Klaxon converters
        val messageJson = klaxonParser().toJsonString(message)
        val messageBuf = messageJson.toByteArray()

        val res = engine.config.dtlsConnect.send(networkSourceKey, messageBuf, messageBuf.size)
        if (res) {
            progress?.invoke(SendProgress.SENT, userId)
        } else {
            progress?.invoke(SendProgress.FAILED, null)
        }
        return res
    }

    fun sendToNetworkForResponse(
        networkSourceKey: NetworkSourceKey,
        userId: String,
        message: Map<String, String>,
        timeoutMs: Long? = null
    ): Map<String, Any?> =
        sendForResponse(message, timeoutMs) {
            sendMessageToNetwork(networkSourceKey, userId, it) { _, _ -> }
        }

    // TODO: move to own class
    private fun advertRecordForId(id: String): AdvertRecord? {
        val advertRecord = engine.nameResolver.resolveIdToAdvertRecord(id)
        if (advertRecord == null) {
            logWarn("Sender::advertRecordForId: ${id} has no DNS entry")

            return null
        }

        logDebug("Sender::advertRecordForId: ${id} located with advert ${advertRecord}")

        if (engine.config.localToLoopback == true && advertRecord.endpoint?.address == engine.currentEndpoint.address) {
            logDebug("Sender::advertRecordForId - Local endpoint, changed to localhost")
            return AdvertRecord(
                advertRecord.id,
                InetSocketAddress("127.0.0.1", advertRecord.endpoint?.port as Int),
                advertRecord.amRelay,
                advertRecord.relayId,
                advertRecord.relaySig
            )
        }

        return advertRecord
    }
}
