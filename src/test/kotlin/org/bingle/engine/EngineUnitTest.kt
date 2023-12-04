package org.bingle.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand
import org.bingle.command.Ping
import org.bingle.command.ResponseCommand
import org.bingle.command.TextMessageCommand
import org.bingle.dtls.DTLSParameters
import org.bingle.dtls.IDTLSConnect
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.mocks.*
import org.bingle.engine.mocks.endpoint2
import org.bingle.engine.mocks.id2
import org.bingle.util.logDebug
import org.bingle.util.logWarn
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import kotlin.reflect.jvm.javaMethod

class EngineUnitTest {
    val mockDtlsConnect = mockk<IDTLSConnect>()
    val mockCommsConfig = MockCommsConfig(mockDtlsConnect)
    lateinit var dtlsParameters: DTLSParameters
    val id1nsk = NetworkSourceKey(endpoint1)
    val id2nsk = NetworkSourceKey(endpoint2)

    init {
        every { mockDtlsConnect.init(any()) } answers {
            dtlsParameters = it.invocation.args[0] as DTLSParameters
        }
        every { mockDtlsConnect.clearAll() } returns Unit
        every { mockDtlsConnect.waitForStopped() } returns Unit
        every { mockDtlsConnect.isInitialized } returns false
        every { mockDtlsConnect.connectionOpenTo("id2") } returns null
    }

    @Test
    fun `Can send ping`() {
        every { mockDtlsConnect.send(id2nsk, any(), any()) } answers {
            val message = Ping.Response().withVerifiedId<Ping.Response>(id2)
            val messageBytes = message.toJson().toByteArray(Charset.defaultCharset())
            dtlsParameters.onMessage(id1nsk, id1, messageBytes, messageBytes.size)
            true
        }

        mockkStatic(::logWarn.javaMethod!!.declaringClass.kotlin)
        every { logWarn(any()) } returns Unit

        val engine = startEngine()

        val sendRes = engine.sendMessage(mockUser2, Ping.Ping())
        assertThat(sendRes).isTrue()

        engine.stop()

        // This tests that the Pinger gets the response
        verify(exactly = 1) { logWarn("Ping response from unknown sender id1") }
    }

    @Test
    fun `Responds to ping`() {
        every { mockDtlsConnect.send(id2nsk, any(), any()) } answers {
            val messageSent = BaseCommand.fromJson((it.invocation.args[1]!! as ByteArray).decodeToString())
            assertThat(messageSent).isInstanceOf(Ping.Response::class.java)
            true
        }

        val engine = startEngine()

        val message = Ping.Ping().withVerifiedId<Ping.Ping>(id2)
        val messageBytes = message.toJson().toByteArray(Charset.defaultCharset())
        dtlsParameters.onMessage(id2nsk, id2, messageBytes, messageBytes.size)

        engine.stop()

        verify(exactly = 1) { mockDtlsConnect.send(id2nsk, any(), any()) }
    }

    private fun startEngine(): Engine {
        val engine = Engine(emptyMap(), mockCommsConfig)
        engine.init()

        while (!engine.listening) {
            Thread.sleep(2000)
        }
        return engine
    }
}