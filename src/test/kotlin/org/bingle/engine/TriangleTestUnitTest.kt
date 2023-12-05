package org.bingle.engine

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand
import org.bingle.command.Ping
import org.bingle.command.RelayCommand
import org.bingle.engine.mocks.*
import org.bingle.engine.mocks.endpoint1
import org.bingle.engine.mocks.endpointRelay
import org.bingle.interfaces.ICommsConfig
import org.bingle.interfaces.NatType
import org.bingle.interfaces.ResolveLevel
import org.junit.jupiter.api.Test

class TriangleTestUnitTest : BaseUnitTest() {

    val mockEngine = mockk<IEngineState>()
    val mockSender = mockk<Sender>()
    val mockCommsConfig = mockk<ICommsConfig>()

    init {
        every { mockEngine.sender } returns mockSender
        every { mockEngine.config } returns mockCommsConfig
        every { mockCommsConfig.onState } returns null
        every { mockCommsConfig.timeouts } returns ICommsConfig.TimeoutConfig(triPing = 1000)
    }

    @Test
    fun `detects full cone when we get a response from second peer`() {
        every { mockEngine.sender.sendToIdForResponse(any(), any(), any()) } answers {
            val messageSent = it.invocation.args[1] as RelayCommand.TriangleTest1
            assertThat(messageSent .checkingEndpoint).isEqualTo(endpoint1)

            RelayCommand.TriangleTest3().withVerifiedId(id2)
        }

        val triangleTest = TriangleTest(mockEngine)
        val nat = triangleTest.determineNatType(ResolveLevel.CONSISTENT, Pair(idRelay, endpointRelay), endpoint1)
        assertThat(nat).isEqualTo(NatType.FULL_CONE)
    }

    @Test
    fun `detects restricted cone when no response`() {
        every { mockEngine.sender.sendToIdForResponse(any(), any(), any()) } returns BaseCommand("timeout")

        val triangleTest = TriangleTest(mockEngine)
        val nat = triangleTest.determineNatType(ResolveLevel.CONSISTENT, Pair(idRelay, endpointRelay), endpoint1)
        assertThat(nat).isEqualTo(NatType.RESTRICTED_CONE)
    }
}