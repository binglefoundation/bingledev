package org.bingle.engine

import com.creatotronik.stun.StunResponse
import org.bingle.command.BaseCommand
import org.bingle.engine.ddb.DdbInitialize
import org.bingle.engine.ddb.DistributedDB
import org.bingle.interfaces.*
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

class ResponseSlot {
    val latch = CountDownLatch(1)
    var msg: BaseCommand? = null

    override fun toString() = "ResponseSlot(msg=$msg)"
}
interface IEngineState {
    fun currentUser(): Pair<String, String>

    val creds: Map<String, String>
    var listening: Boolean
    var currentEndpoint: InetSocketAddress
    var currentRelay: PopulatedRelayInfo?
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

    // TODO: gets replaced with DDB
    var resolver: IResolver

    // TODO: own class
    val stunProcessor: StunProcessor
    val stunResolver: IStunResolver
    var stunResponseThread: Thread
    var stunHandlerDone: Boolean
    val stunHandlerQueue: LinkedBlockingQueue<StunResponse>
    val responseSlots: MutableMap<String, ResponseSlot>

    var distributedDB: DistributedDB
    val ddbInitialize: DdbInitialize
    var ddbWaitingForLoadLatch: CountDownLatch?

    val commandRouter: CommandRouter
    val triangleTest: TriangleTest

    val relay: Relay
    val turnRelayProtocol: TurnRelayProtocol

}