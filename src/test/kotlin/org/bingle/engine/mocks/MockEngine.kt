package org.bingle.engine.mocks

import com.creatotronik.stun.StunResponse
import org.bingle.dtls.IDTLSConnect
import org.bingle.engine.*
import org.bingle.engine.IEngineState
import org.bingle.engine.ddb.DdbInitialize
import org.bingle.engine.ddb.DistributedDB
import org.bingle.interfaces.*
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

class MockEngine(mockDtlsConnect: IDTLSConnect) : IEngineState {
    override fun currentUser(): Pair<String, String> {
        return Pair(
            keyProvider.getId()!!,
            myUsername
        )
    }

    override val creds: Map<String, String> = mapOf("username" to mockUser1)

    override var listening: Boolean = false

    override var currentEndpoint: InetSocketAddress
        get() = TODO("Not yet implemented")
        set(value) {}

    override var currentRelay: PopulatedRelayInfo? = null

    override var myUsername: String = mockUser1

    override var id: String = id1
    override val config: ICommsConfig = MockCommsConfig(mockDtlsConnect)
    override var state: CommsState = CommsState.NONE
    override val worker: Worker
        get() = TODO("Not yet implemented")
    override val sender: Sender
        get() = TODO("Not yet implemented")

    override val keyProvider: IKeyProvider = config.makeKeyProvider(creds)

    override val chainAccess = MockChainAccess()
    override var relayFinder: RelayFinder
        get() = TODO("Not yet implemented")
        set(value) {}

    override val networkChangeProvider: INetworkChangeProvider = MockNetworkChangeProvider()

    override val advertiser: IAdvertiser
        get() = TODO("Not yet implemented")

    override var pinger: Pinger = Pinger(this, config.requestPingables, config.onAvailability)

    override var resolver: IResolver = MockResolver()
    override val stunProcessor = StunProcessor(this)
    override val stunResolver: IStunResolver = StunResolver()

    override lateinit var stunResponseThread: Thread

    override var stunHandlerDone: Boolean = false

    override val stunHandlerQueue: LinkedBlockingQueue<StunResponse> = LinkedBlockingQueue()

    override val turnRelayProtocol: TurnRelayProtocol
        get() = TODO("Not yet implemented")
    override val responseSlots = mutableMapOf<String, ResponseSlot>()
    override var distributedDB: DistributedDB
        get() = TODO("Not yet implemented")
        set(value) {}
    override var ddbInitialize = DdbInitialize(this)
    override var ddbWaitingForLoadLatch: CountDownLatch? = null

    private val myCommandRouter = CommandRouter(this)
    override val commandRouter: CommandRouter
        get() = myCommandRouter
    override val triangleTest: TriangleTest = TriangleTest(this)
    override var relay = Relay(this)

}