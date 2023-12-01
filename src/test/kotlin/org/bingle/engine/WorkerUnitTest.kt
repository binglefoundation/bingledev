package org.bingle.engine

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand.Companion.klaxonParser
import org.bingle.dtls.DTLSParameters
import org.bingle.dtls.IDTLSConnect
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.mocks.MockEngine
import org.bingle.engine.mocks.endpoint2
import org.bingle.engine.mocks.id2
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class WorkerUnitTest : BaseUnitTest() {

    @Test
    fun `Can start and stop worker`() {
        val mockDtlsConnect = mockk<IDTLSConnect>()

        every { mockDtlsConnect.clearAll() } returns Unit
        every { mockDtlsConnect.waitForStopped() } returns Unit
        every { mockDtlsConnect.isInitialized } returns true
        every { mockDtlsConnect.restart() } returns Unit

        val mockEngine = MockEngine(mockDtlsConnect)

        val worker = Worker(mockEngine)
        worker.start()

        while(!mockEngine.listening) {
            Thread.sleep(2000)
        }

        worker.stop()
    }

    @Test
    fun `Can receive and pass on a message`() {
        val mockDtlsConnect = mockk<IDTLSConnect>()

        lateinit var dtlsParameters: DTLSParameters
        every { mockDtlsConnect.init(any()) } answers {
            dtlsParameters = it.invocation.args[0] as DTLSParameters
        }
        every { mockDtlsConnect.clearAll() } returns Unit
        every { mockDtlsConnect.waitForStopped() } returns Unit
        every { mockDtlsConnect.isInitialized } returns false

        val mockEngine = MockEngine(mockDtlsConnect)
        var hadMessage = false
        mockEngine.config.onMessage = {
            assertThat(it).isNotNull
            assertThat(it["text"]).isEqualTo("Good morning")
            hadMessage = true
        }

        val worker = Worker(mockEngine)
        worker.start()

        while(!mockEngine.listening) {
            Thread.sleep(2000)
        }

        val id2nsk = NetworkSourceKey(endpoint2)
        val message = mapOf("text" to "Good morning")
        val messageBytes = klaxonParser().toJsonString(message).toByteArray(Charset.defaultCharset())
        dtlsParameters.onMessage(id2nsk, id2, messageBytes, messageBytes.size)
        assertThat(hadMessage).isTrue()

        worker.stop()
    }
}