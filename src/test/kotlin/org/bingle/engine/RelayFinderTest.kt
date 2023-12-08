import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.IEngineState
import org.bingle.engine.RelayFinder
import org.bingle.engine.RelayIdToAddress
import org.bingle.engine.Sender
import org.bingle.engine.mocks.endpointRelay
import org.bingle.engine.mocks.id1
import org.bingle.engine.mocks.idRelay
import org.bingle.interfaces.IChainAccess
import org.bingle.interfaces.ICommsConfig
import org.junit.jupiter.api.Test


class RelayFinderTest {
    val mockEngine = mockk<IEngineState>()
    val mockChainAccess = mockk<IChainAccess>()
    val mockSender = mockk<Sender>()
    val relayFinder = RelayFinder(mockChainAccess, id1, mockEngine)

    @Test
    fun `finds a single configured relay`() {
        val mockCommsConfig = mockk<ICommsConfig>()
        every { mockEngine.currentRelay } returns null
        every { mockEngine.config } returns mockCommsConfig
        every { mockEngine.sender } returns mockSender
        every { mockCommsConfig.alwaysRelayWithId } returns null
        every { mockChainAccess.listRelaysWithIps() } returns listOf(Pair(idRelay, endpointRelay))
        every { mockCommsConfig.onState } returns null
        every { mockCommsConfig.timeouts } returns ICommsConfig.TimeoutConfig(triPing = 1000)

        every {
            mockEngine.sender.sendToNetworkForResponse(
                NetworkSourceKey(endpointRelay),
                idRelay,
                any(RelayCommand.Check::class),
                any()
            )
        } answers {
            RelayCommand.CheckResponse(1)
        }

        every { mockEngine.currentRelay = any() } answers {
            assertThat(it.invocation.args[0] as RelayIdToAddress).isEqualTo(Pair(idRelay, endpointRelay))
        }

        relayFinder.find()
        verify { mockEngine.currentRelay = any() }
    }
}