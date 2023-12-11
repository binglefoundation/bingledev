package org.bingle.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.bingle.command.BaseCommand
import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.TriangleTestUnitTest.Companion.mockTriangleTestResponder
import org.bingle.engine.mocks.*
import org.bingle.engine.mocks.endpoint1
import org.bingle.engine.mocks.endpointRelay
import org.bingle.interfaces.CommsState
import org.bingle.interfaces.IAdvertiser
import org.bingle.interfaces.ICommsConfig
import org.bingle.interfaces.ResolveLevel
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class RelayUnitTest : BaseUnitTest() {
    private val mockEngine = mockk<IEngineState>()
    private val mockSender = mockk<Sender>()
    private val mockAdvertiser = mockk<IAdvertiser>()
    override var mockCommsConfig = mockk<ICommsConfig>()
    private val mockKeyProvider = MockKeyProvider()

    private val relay = Relay(mockEngine)

    init {
        every { mockEngine.sender } returns mockSender
        every { mockEngine.config } returns mockCommsConfig
        every { mockEngine.advertiser } returns mockAdvertiser
        every { mockEngine.triangleTest } returns TriangleTest(mockEngine)
        every { mockEngine.relay } returns relay
        every { mockEngine.keyProvider } returns mockKeyProvider
        every { mockEngine.worker.initDDBApp(any()) } answers {}
        every { mockEngine.currentEndpoint = any() } answers {}

        every { mockCommsConfig.onState } returns null
        every { mockCommsConfig.timeouts } returns ICommsConfig.TimeoutConfig(triPing = 1000)
    }

    private fun setupRegularConfig() {
        every { mockCommsConfig.useRelays } returns true
        every { mockCommsConfig.forceRelay } returns false
        every { mockCommsConfig.alwaysRelayWithId } returns null
        every { mockCommsConfig.isRelay } returns false
    }

    @Test
    fun `A user with full cone and no special settings connects without relay`() {
        setupRegularConfig()
        every { mockEngine.relayFinder.find() } returns PopulatedRelayInfo(idRelay, endpointRelay)
        every {
            mockEngine.sender.sendToNetworkForResponse(
                NetworkSourceKey(endpointRelay),
                idRelay,
                any(RelayCommand.TriangleTest1::class),
                any()
            )
        } answers {
            mockTriangleTestResponder(it)
        }

        every { mockEngine.state = any() } answers {
            assertThat(it.invocation.args[0]).isEqualTo(CommsState.ADVERTISED)
        }
        every { mockAdvertiser.advertise(any(), any()) } answers {
            val myEndpoint = it.invocation.args[1] as InetSocketAddress
            assertThat(myEndpoint).isEqualTo(endpoint1)
        }

        relay.adoptRelayState(endpoint1, ResolveLevel.CONSISTENT)

        verify { mockEngine.state = any() }
        verify { mockAdvertiser.advertise(any(), any()) }
    }

    @Test
    fun `A user with restricted cone and no special settings connects via relay`() {
        setupRegularConfig()
        every { mockEngine.relayFinder.find() } returns PopulatedRelayInfo(idRelay, endpointRelay)
        every {
            mockEngine.sender.sendToNetworkForResponse(
                NetworkSourceKey(endpointRelay),
                idRelay,
                any(RelayCommand.TriangleTest1::class),
                any()
            )
        } answers {
            BaseCommand("timeout")
        }
        every { mockEngine.sender.sendToIdForResponse(idRelay, any(), any()) } answers {
            val command = it.invocation.args[1]
            when (command) {
                is RelayCommand.Listen -> {
                    BaseCommand()
                }

                else -> {
                    fail("${command} unexpected by relay")
                }
            }
        }

        every { mockEngine.state = any() } answers {
            assertThat(it.invocation.args[0]).isEqualTo(CommsState.ADVERTISED)
        }
        every { mockAdvertiser.advertiseUsingRelay(any(), any()) } answers {
            val relayId = it.invocation.args[1] as String
            assertThat(relayId).isEqualTo(idRelay)
        }

        relay.adoptRelayState(endpoint1, ResolveLevel.CONSISTENT)

        verify { mockEngine.state = any() }
        verify { mockAdvertiser.advertiseUsingRelay(any(), any()) }
        verify { mockEngine.sender.sendToIdForResponse(idRelay, any(RelayCommand.Listen::class), any()) }
    }

    @Test
    fun `A user with symmetric NAT and no special settings connects via relay`() {
        setupRegularConfig()
        every { mockEngine.relayFinder.find() } returns PopulatedRelayInfo(idRelay, endpointRelay)
        every { mockEngine.sender.sendToIdForResponse(idRelay, any(), any()) } answers {
            val command = it.invocation.args[1]
            if (command is RelayCommand.Listen) {
                BaseCommand()
            } else {
                fail("${command} unexpected by relay")
            }
        }

        every { mockEngine.state = any() } answers {
            assertThat(it.invocation.args[0]).isEqualTo(CommsState.ADVERTISED)
        }
        every { mockAdvertiser.advertiseUsingRelay(any(), any()) } answers {
            val relayId = it.invocation.args[1] as String
            assertThat(relayId).isEqualTo(idRelay)
        }

        val randomSymEndpoint = InetSocketAddress("5.4.3.2", 1234)
        every { mockEngine.currentEndpoint = any() } answers {
            assertThat(it.invocation.args[0]).isEqualTo(randomSymEndpoint)
        }

        relay.adoptRelayState(randomSymEndpoint, ResolveLevel.INCONSISTENT)

        verify { mockEngine.state = any() }
//        verify { mockEngine.currentEndpoint = any() }
        verify { mockAdvertiser.advertiseUsingRelay(any(), any()) }
        verify { mockEngine.sender.sendToIdForResponse(idRelay, any(RelayCommand.Listen::class), any()) }
    }

    @Test
    fun `A user with full cone and isRelay set connects and becomes a relay`() {
        every { mockCommsConfig.useRelays } returns true
        every { mockCommsConfig.forceRelay } returns false
        every { mockCommsConfig.alwaysRelayWithId } returns null
        every { mockCommsConfig.isRelay } returns true
        every { mockEngine.relayFinder.find() } returns PopulatedRelayInfo(idRelay, endpointRelay)
        every {
            mockEngine.sender.sendToNetworkForResponse(
                NetworkSourceKey(endpointRelay),
                idRelay,
                any(RelayCommand.TriangleTest1::class),
                any()
            )
        } answers {
            mockTriangleTestResponder(it)
        }
        every { mockEngine.currentEndpoint } returns endpoint1

        var mockedState: CommsState? = null
        every { mockEngine.state = any() } answers {
            mockedState = it.invocation.args[0] as CommsState
        }
        every { mockAdvertiser.advertise(any(), any()) } answers {
            val myEndpoint = it.invocation.args[1] as InetSocketAddress
            assertThat(myEndpoint).isEqualTo(endpoint1)
        }
        every { mockEngine.currentEndpoint = any() } answers {
            assertThat(it.invocation.args[0]).isEqualTo(endpoint1)
        }
        every { mockAdvertiser.advertiseAmRelay(any(), any(), any()) } answers {
            val endpoint = it.invocation.args[1] as InetSocketAddress
            assertThat(endpoint).isEqualTo(endpoint1)
            val relayEndpoint = it.invocation.args[2] as InetSocketAddress
            assertThat(relayEndpoint).isEqualTo(endpoint1)
        }

        relay.adoptRelayState(endpoint1, ResolveLevel.CONSISTENT)

        verify { mockEngine.state = any() }
        verify { mockAdvertiser.advertise(any(), any()) }
        verify { mockAdvertiser.advertiseAmRelay(any(), any(), any()) }
        assertThat(mockedState).isEqualTo(CommsState.RELAY_ADVERTISED)
    }

    // TODO: test the various special config settings
}