package org.bingle.engine

import com.creatotronik.stun.StunResponse
import com.creatotronik.stun.StunResponseKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.dtls.IDTLSConnect
import org.bingle.engine.mocks.MockEngine
import org.bingle.interfaces.CommsState
import org.bingle.interfaces.ResolveLevel
import java.net.InetSocketAddress
import kotlin.test.Test

class StunProcessorUnitTest : BaseUnitTest() {

    @Test
    fun `calls adoptRelayState with CONSISTENT after stun responses are consistent`() {
        val myEndpoint = InetSocketAddress("5.0.0.0", 1000)
        val mockDtlsConnect = mockk<IDTLSConnect>()
        val mockEngine = MockEngine(mockDtlsConnect)
        mockEngine.relay = mockk<Relay>()
        every {
            mockEngine.relay.adoptRelayState(myEndpoint, ResolveLevel.CONSISTENT)
        } returns Unit

        val stunProcessor = StunProcessor(mockEngine)
        stunProcessor.runResponseThread()

        mockEngine.stunHandlerQueue.add(StunResponse(StunResponseKind.PLAIN, "stun1", myEndpoint))
        Thread.sleep(500)
        assertThat(mockEngine.state).isEqualTo(CommsState.NONE)

        mockEngine.stunHandlerQueue.add(StunResponse(StunResponseKind.PLAIN, "stun2", myEndpoint))
        Thread.sleep(500)
        assertThat(mockEngine.state).isEqualTo(CommsState.BOUND)
        verify { mockEngine.relay.adoptRelayState(myEndpoint, ResolveLevel.CONSISTENT) }
    }

    @Test
    fun `calls adoptRelayState with INCONSISTENT after stun responses are inconsistent`() {
        val myEndpoint = InetSocketAddress("5.0.0.0", 1000)
        val mockDtlsConnect = mockk<IDTLSConnect>()
        val mockEngine = MockEngine(mockDtlsConnect)
        mockEngine.relay = mockk<Relay>()
        every {
            mockEngine.relay.adoptRelayState(myEndpoint, ResolveLevel.INCONSISTENT)
        } returns Unit

        val stunProcessor = StunProcessor(mockEngine)
        stunProcessor.runResponseThread()

        mockEngine.stunHandlerQueue.add(StunResponse(StunResponseKind.PLAIN, "stun1", myEndpoint))
        Thread.sleep(500)
        assertThat(mockEngine.state).isEqualTo(CommsState.NONE)

        mockEngine.stunHandlerQueue.add(
            StunResponse(
                StunResponseKind.PLAIN,
                "stun2",
                InetSocketAddress("5.0.0.1", 777)
            ))
        Thread.sleep(500)
        assertThat(mockEngine.state).isEqualTo(CommsState.BOUND)
        verify { mockEngine.relay.adoptRelayState(myEndpoint, ResolveLevel.INCONSISTENT) }
    }

}