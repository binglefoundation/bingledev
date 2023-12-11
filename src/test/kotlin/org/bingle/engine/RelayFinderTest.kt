import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand
import org.bingle.command.DdbCommand
import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.*
import org.bingle.engine.mocks.endpointRelay
import org.bingle.engine.mocks.id1
import org.bingle.engine.mocks.idRelay
import org.bingle.interfaces.IChainAccess
import org.bingle.interfaces.ICommsConfig
import org.bingle.interfaces.IResolver
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.*


class RelayFinderTest : BaseUnitTest() {
    val mockEngine = mockk<IEngineState>()
    val mockChainAccess = mockk<IChainAccess>()
    val mockSender = mockk<Sender>()
    val relayFinder = RelayFinder(mockChainAccess, id1, mockEngine)
    override var mockCommsConfig = mockk<ICommsConfig>()

    val idRootRelay2 = "idRootRelay2"
    val endpointRootRelay2 = InetSocketAddress("1.1.1.11", 3333)
    val idRelay3 = "idRelay3"
    val endpointRelay3 = InetSocketAddress("15.1.1.3", 3333)
    val idRelay4 = "idRelay4"
    val endpointRelay4 = InetSocketAddress("15.1.1.4", 3333)

    init {
        every { mockEngine.currentRelay } returns null
        every { mockEngine.config } returns mockCommsConfig
        every { mockEngine.sender } returns mockSender
        every { mockCommsConfig.alwaysRelayWithId } returns null
        every { mockCommsConfig.onState } returns null
        every { mockCommsConfig.timeouts } returns ICommsConfig.TimeoutConfig(triPing = 1000)

        mockSenderSendToNetworkForResponse(mockEngine,
            NetworkSourceKey(endpointRelay),
            idRelay,
            RelayCommand.Check::class) {
            RelayCommand.CheckResponse(1)
        }
    }

    @Test
    fun `finds a single configured root relay`() {
        every { mockChainAccess.listRelaysWithIps() } returns listOf(RelayInfo(idRelay, endpointRelay, true))

        setupRelay(idRelay, endpointRelay, listOf(idRelay))

        every { mockEngine.currentRelay = any() } answers {
            assertThat(it.invocation.args[0] as PopulatedRelayInfo).isEqualTo(PopulatedRelayInfo(idRelay,endpointRelay))
        }

        relayFinder.find()
        verify { mockEngine.currentRelay = any() }
    }

    @Test
    fun `finds one of two configured root relays`() {
        every { mockChainAccess.listRelaysWithIps() } returns listOf(
            RelayInfo(idRelay, endpointRelay, true),
            RelayInfo(idRootRelay2, endpointRootRelay2, true),
            )

        setupRelay(idRelay, endpointRelay, listOf(idRelay, idRootRelay2))
        setupRelay(idRootRelay2, endpointRootRelay2, listOf(idRelay, idRootRelay2))

        every { mockEngine.currentRelay = any() } answers {
            assertThat(it.invocation.args[0] as PopulatedRelayInfo).isEqualTo(PopulatedRelayInfo(idRootRelay2,endpointRootRelay2))
        }

        relayFinder.find()
        verify { mockEngine.currentRelay = any() }
    }

    @Test
    fun `finds one of two configured root relays and two floating relays`() {
        every { mockChainAccess.listRelaysWithIps() } returns listOf(
            RelayInfo(idRelay, endpointRelay, true),
            RelayInfo(idRootRelay2, endpointRootRelay2, true),
            RelayInfo(idRelay3),
            RelayInfo(idRelay4),
        )

        val allActiveRelays = listOf(idRelay, idRootRelay2, idRelay3, idRelay4)
        setupRelay(idRelay, endpointRelay, allActiveRelays)
        setupRelay(idRootRelay2, endpointRootRelay2, allActiveRelays)
        setupRelay(idRelay3, endpointRelay3, allActiveRelays)
        setupRelay(idRelay4, endpointRelay4, allActiveRelays)

        every { mockEngine.currentRelay = any() } answers {
            assertThat(it.invocation.args[0] as PopulatedRelayInfo).isEqualTo(PopulatedRelayInfo(idRelay4, endpointRelay4))
        }

        val mockResolver = mockk<IResolver>()
        every { mockEngine.resolver } returns mockResolver
        every { mockResolver.resolveIdToRelay(idRelay3) } returns IResolver.RelayDns(endpointRelay3, Date())
        every { mockResolver.resolveIdToRelay(idRelay4) } returns IResolver.RelayDns(endpointRelay4, Date())

        relayFinder.find()
        verify { mockEngine.currentRelay = any() }
    }

    @Test
    fun `finds alternate relay when one is not responsive`() {
        every { mockChainAccess.listRelaysWithIps() } returns listOf(
            RelayInfo(idRelay, endpointRelay, true),
            RelayInfo(idRootRelay2, endpointRootRelay2, true),
            RelayInfo(idRelay3),
            RelayInfo(idRelay4),
        )

        val allActiveRelays = listOf(idRelay, idRootRelay2, idRelay3, idRelay4)
        setupRelay(idRelay, endpointRelay, allActiveRelays)
        setupRelay(idRootRelay2, endpointRootRelay2, allActiveRelays)
        setupRelay(idRelay3, endpointRelay3, allActiveRelays)
        mockSenderSendToNetworkForResponse(
            mockEngine,
            NetworkSourceKey(endpointRelay4),
            idRelay4,
            RelayCommand.Check::class
        ) {
            BaseCommand("timeout")
        }

        every { mockEngine.currentRelay = any() } answers {
            assertThat(it.invocation.args[0] as PopulatedRelayInfo).isEqualTo(PopulatedRelayInfo(idRootRelay2, endpointRootRelay2))
        }

        val mockResolver = mockk<IResolver>()
        every { mockEngine.resolver } returns mockResolver
        every { mockResolver.resolveIdToRelay(idRelay3) } returns IResolver.RelayDns(endpointRelay3, Date())
        every { mockResolver.resolveIdToRelay(idRelay4) } returns IResolver.RelayDns(endpointRelay4, Date())

        relayFinder.find()
        verify { mockEngine.currentRelay = any() }
    }

    private fun setupRelay(id: String, endpoint: InetSocketAddress, allActiveRelays: List<String>) {
        mockSenderSendToNetworkForResponse(
            mockEngine,
            NetworkSourceKey(endpoint),
            id,
            DdbCommand.GetEpoch::class
        ) {
            DdbCommand.GetEpochResponse(1, 1, allActiveRelays)
        }
        mockSenderSendToNetworkForResponse(
            mockEngine,
            NetworkSourceKey(endpoint),
            id,
            RelayCommand.Check::class
        ) {
            RelayCommand.CheckResponse(1)
        }
    }

}