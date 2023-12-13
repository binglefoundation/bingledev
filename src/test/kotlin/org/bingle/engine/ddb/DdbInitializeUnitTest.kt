package org.bingle.engine.ddb

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.DdbCommand
import org.bingle.command.data.AdvertRecord
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.*
import org.bingle.engine.mocks.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch

class DdbInitializeUnitTest : BaseUnitTest() {

    private lateinit var distributedDB: DistributedDB
    private lateinit var ddbWaitingForLoadLatch: CountDownLatch

    private val mockEngine = mockk<IEngineState>()
    private val mockRelayFinder = mockk<RelayFinder>()
    private val mockSender = mockk<Sender>()

    init {
        every { mockEngine.id } returns id1
        every { mockEngine.config } returns mockCommsConfig
        every { mockEngine.distributedDB = any() } answers { distributedDB = it.invocation.args[0] as DistributedDB }
        every { mockEngine.distributedDB } answers { distributedDB }
        every { mockEngine.ddbWaitingForLoadLatch = any() } answers {
            ddbWaitingForLoadLatch = it.invocation.args[0] as CountDownLatch
        }
        every { mockEngine.ddbWaitingForLoadLatch } answers { ddbWaitingForLoadLatch }
        every { mockEngine.relayFinder } returns mockRelayFinder
        every { mockEngine.sender } returns mockSender
    }

    @Test
    fun `bootstraps when no peer relays`() {
        every { mockEngine.relayFinder } returns mockRelayFinder
        every { mockRelayFinder.find() } returns null

        val ddbInitialize = DdbInitialize(mockEngine)

        ddbInitialize.becomeRelay(endpointRelay)

        assertThat(distributedDB.records).isEmpty()
    }

    @Test
    fun `bootstraps from a peer relay`() {
        val peerRecords = mapOf(
            id1 to AdvertRecord(id1, endpoint1),
            id2 to AdvertRecord(id2, endpoint2),
            idRelay to AdvertRecord(idRelay, endpointRelay)
        )

        val commandRouter = CommandRouter(mockEngine)

        every { mockRelayFinder.find() } returns PopulatedRelayInfo(idRelay, endpointRelay)
        every {
            mockEngine.sender.sendToNetworkForResponse(
                NetworkSourceKey(endpointRelay),
                idRelay,
                match { it.javaClass === DdbCommand.InitResolve::class.java },
                any()
            )
        } answers {
            Thread {
                Thread.sleep(1000)
                peerRecords.forEach { (id, advertRecord) ->
                    commandRouter.routeCommand(DdbCommand.DumpResolve(id, advertRecord).withVerifiedId(idRelay))
                }
            }.start()

            DdbCommand.InitResponse(peerRecords.size).withVerifiedId<DdbCommand.InitResponse>(idRelay)
        }
        mockSenderSendToNetworkForResponse(
            mockEngine,
            NetworkSourceKey(endpointRelay),
            idRelay,
            DdbCommand.GetEpoch::class
        ) {
            DdbCommand.GetEpochResponse(1, 1, listOf(idRelay))
        }
       every {
           mockEngine.sender.sendMessageToId(idRelay, match {it.javaClass === DdbCommand.Signon::class.java}, any())
       } returns true

        val ddbInitialize = DdbInitialize(mockEngine)

        ddbInitialize.becomeRelay(endpoint1)

        assertThat(distributedDB.records).isEqualTo(peerRecords)
        verify {
            mockEngine.sender.sendMessageToId(idRelay, match {it.javaClass === DdbCommand.Signon::class.java}, any())
        }
    }
}