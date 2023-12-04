package org.bingle.engine

import com.creatotronik.stun.StunResponse
import org.bingle.command.BaseCommand
import org.bingle.going.apps.ddb.DistributedDBApp
import org.bingle.interfaces.CommsState
import org.bingle.interfaces.ICommsConfig
import org.bingle.interfaces.SendProgress
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
    override val triangleTest = TriangleTest(this)

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
    override lateinit var distributedDBApp: DistributedDBApp

    override val commandRouter = CommandRouter(this)

    fun init() {
        worker.start()
    }

    fun stop() {
        worker.stop()
    }

    fun sendMessage(
        username: String,
        message: BaseCommand,
        progress: ((p: SendProgress, id: String?) -> Unit)? = null
    ): Boolean {
        return sender.sendMessage(username, message, progress)
    }

    fun sendMessageToId(
        userId: String,
        message: BaseCommand,
        progress: ((p: SendProgress, id: String?) -> Unit)? = null
    ): Boolean {
        return sender.sendMessageToId(userId, message, progress)
    }

    fun sendToIdForResponse(
        userId: String,
        message: BaseCommand,
        timeoutMs: Long? = null
    ): BaseCommand {
        return sender.sendToIdForResponse(userId, message, timeoutMs)
    }

    override fun currentUser(): Pair<String, String> {
        return Pair(
            keyProvider.getId()!!,
            myUsername
        )
    }
}