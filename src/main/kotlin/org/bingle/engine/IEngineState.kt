package org.bingle.engine

import com.creatotronik.stun.StunResponse
import org.bingle.command.BaseCommand
import org.bingle.going.apps.ddb.DistributedDBApp
import org.bingle.interfaces.*
import org.bingle.interfaces.going.IApp
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

class ResponseSlot {
    val latch = CountDownLatch(1)
    var msg: BaseCommand? = null

    override fun toString() = "ResponseSlot(msg=$msg)"
}
interface IEngineState {
    abstract fun currentUser(): Pair<String, String>

    val creds: Map<String, String>
    var listening: Boolean
    var currentEndpoint: InetSocketAddress
    var myUsername: String
    var id: String
    val config: ICommsConfig
    var state: CommsState
    val worker: Worker
    val sender: Sender
    val keyProvider: IKeyProvider
    val chainAccess: IChainAccess
    var relayFinder: RelayFinder
    val networkChangeProvider: INetworkChangeProvider
    val advertiser: IAdvertiser
    var pinger: Pinger

    // TODO: gets replaced with relay
    val nameResolver: IResolver

    // TODO: own class
    val stunResolver: IStunResolver
    var stunResponseThread: Thread
    var stunHandlerDone: Boolean
    val stunHandlerQueue: LinkedBlockingQueue<StunResponse>
    val relay: TurnRelayProtocol
    val responseSlots: MutableMap<String, ResponseSlot>

    // TODO: remove apps
    val apps: MutableMap<String, IApp>
    var distributedDBApp: DistributedDBApp

    // TODO:own class

    val commandRouter: CommandRouter
}