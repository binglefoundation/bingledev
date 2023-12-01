package org.bingle.engine

import com.creatotronik.stun.StunResponse
import org.bingle.going.apps.PingApp
import org.bingle.going.apps.RelayApp
import org.bingle.going.apps.ddb.DistributedDBApp
import org.bingle.interfaces.CommsState
import org.bingle.interfaces.ICommsConfig
import org.bingle.interfaces.SendProgress
import org.bingle.interfaces.going.IApp
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue

class Engine(override val creds: Map<String, String>, override val config: ICommsConfig) : IEngineState {
    override var listening = false
    override var state: CommsState = CommsState.NONE
    override lateinit var currentEndpoint: InetSocketAddress
    override lateinit var myUsername: String
    override lateinit var id: String
    override val worker: Worker = Worker(this)
    override val sender: Sender = Sender(this)
    override val keyProvider = config.makeKeyProvider(creds)
    override val chainAccess = config.makeChainAccess(keyProvider)
    override lateinit var relayFinder: RelayFinder
    override val networkChangeProvider = config.makeNetworkChangeProvider()
    override val advertiser = config.makeAdvertiser()
    override lateinit var pinger: Pinger

    // TODO: gets replaced with relay
    override val nameResolver = config.makeResolver()

    // TODO: own class
    override val stunResolver = config.makeStunResolver()
    override lateinit var stunResponseThread: Thread
    override var stunHandlerDone: Boolean = false
    override val stunHandlerQueue = LinkedBlockingQueue<StunResponse>()

    override val relay: TurnRelayProtocol get() = config.dtlsConnect.userRelay

    override val responseSlots: MutableMap<String, ResponseSlot> = mutableMapOf()

    // TODO: remove apps
    override val apps: MutableMap<String, IApp> =
        listOf(PingApp(this), RelayApp(this)).associateBy { it.type }.toMutableMap()
    override lateinit var distributedDBApp: DistributedDBApp

    fun init() {
        worker.start()
    }

    fun stop() {
        worker.stop()
    }

    fun sendMessage(
        username: String,
        message: Map<String, Any?>,
        progress: (p: SendProgress, id: String?) -> Unit
    ): Boolean {
        return sender.sendMessage(username, message, progress)
    }

    fun sendMessageToId(
        userId: String,
        message: Map<String, Any?>,
        progress: ((p: SendProgress, id: String?) -> Unit)? = null
    ): Boolean {
        return sender.sendMessageToId(userId, message, progress)
    }

    fun sendToIdForResponse(
        userId: String,
        message: Map<String, Any?>,
        timeoutMs: Long? = null
    ): Map<String, Any?> {
        return sender.sendToIdForResponse(userId, message, timeoutMs)
    }

    override fun currentUser(): Pair<String, String> {
        return Pair(
            keyProvider.getId()!!,
            myUsername
                ?: "Engine:currentUser called when no valid user"
        )
    }
}