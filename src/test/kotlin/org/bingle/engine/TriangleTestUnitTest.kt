package org.bingle.engine

import io.mockk.Call
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand
import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.mocks.*
import org.bingle.interfaces.ICommsConfig
import org.bingle.interfaces.NatType
import org.bingle.interfaces.ResolveLevel
import org.junit.jupiter.api.Test

class TriangleTestUnitTest : BaseUnitTest() {

    private val mockEngine = mockk<IEngineState>()
    private val mockSender = mockk<Sender>()

    init {
        every { mockEngine.sender } returns mockSender
        every { mockEngine.config } returns mockCommsConfig
        // every { mockCommsConfig.onState } returns null
        // every { mockCommsConfig.timeouts } returns ICommsConfig.TimeoutConfig(triPing = 1000)
    }

    companion object {
        fun mockTriangleTestResponder(mockCall: Call): BaseCommand {
            val messageSent = mockCall.invocation.args[2] as RelayCommand.TriangleTest1
            assertThat(messageSent.checkingEndpoint).isEqualTo(endpoint1)

            return RelayCommand.TriangleTest3().withVerifiedId(id2)
        }
    }

    @Test
    fun `TriangleTest detects full cone when we get a response from second peer`() {
        every { mockEngine.sender.sendToNetworkForResponse(any(), any(), any(), any()) } answers {
            mockTriangleTestResponder(it)
        }

        val triangleTest = TriangleTest(mockEngine)
        val nat = triangleTest.determineNatType(ResolveLevel.CONSISTENT, PopulatedRelayInfo(idRelay,endpointRelay), endpoint1)
        assertThat(nat).isEqualTo(NatType.FULL_CONE)
    }

    @Test
    fun `TriangleTest detects restricted cone when no response`() {
        every { mockEngine.sender.sendToNetworkForResponse(any(), any(), any(), any()) } returns BaseCommand("timeout")

        val triangleTest = TriangleTest(mockEngine)
        val nat = triangleTest.determineNatType(ResolveLevel.CONSISTENT, PopulatedRelayInfo(idRelay,endpointRelay), endpoint1)
        assertThat(nat).isEqualTo(NatType.RESTRICTED_CONE)
    }

    @Test
    fun `TriangleTest1Handler forwards the message`() {
        every { mockEngine.relayFinder.find() } returns PopulatedRelayInfo(idRelay,endpointRelay)
        every { mockEngine.sender.sendMessageToNetwork(any(), any(), any(), any()) } answers {
            val toId = it.invocation.args[0] as NetworkSourceKey
            assertThat(toId.inetSocketAddress).isEqualTo(endpointRelay)
            val messageSent = it.invocation.args[2] as RelayCommand.TriangleTest2
            assertThat(messageSent.checkingId).isEqualTo(id1)
            assertThat(messageSent.checkingEndpoint).isEqualTo(endpoint1)

            true
        }

        triangleTest1Handler(
            mockEngine,
            RelayCommand.TriangleTest1(endpoint1)
                .withVerifiedId<RelayCommand.TriangleTest1>(id1)
                .withResponseTag("12345")
        )

        verify { mockEngine.sender.sendMessageToNetwork(any(), any(), any(), any()) }
    }

    @Test
    fun `TriangleTest2Handler returns the message`() {
        every { mockEngine.sender.sendMessageToNetwork(any(), any(), any(), any()) } answers {
            val toNetworkSourceKey = it.invocation.args[0] as NetworkSourceKey
            assertThat(toNetworkSourceKey).isEqualTo(NetworkSourceKey(endpoint1))
            val messageSent = it.invocation.args[2] as RelayCommand.TriangleTest3
            assertThat(messageSent.tag).isEqualTo("12345")

            true
        }

        triangleTest2Handler(
            mockEngine,
            RelayCommand.TriangleTest2(id1, endpoint1)
                .withVerifiedId<RelayCommand.TriangleTest2>(idRelay)
                .withResponseTag("12345")
        )

        verify { mockEngine.sender.sendMessageToNetwork(any(), any(), any(), any()) }
    }
}