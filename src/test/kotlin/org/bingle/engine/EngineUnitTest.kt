package org.bingle.engine

import com.creatotronik.stun.StunResponse
import com.creatotronik.stun.StunResponseKind
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand
import org.bingle.command.DdbCommand
import org.bingle.command.Ping
import org.bingle.command.RelayCommand
import org.bingle.command.data.AdvertRecord
import org.bingle.dtls.DTLSParameters
import org.bingle.engine.mocks.*
import org.bingle.util.logWarn
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import kotlin.reflect.jvm.javaMethod

class EngineUnitTest : BaseUnitTest() {
    init {
        every { mockDtlsConnect.init(any()) } answers {
            dtlsParameters = it.invocation.args[0] as DTLSParameters
        }
        every { mockDtlsConnect.clearAll() } returns Unit
        every { mockDtlsConnect.waitForStopped() } returns Unit
        every { mockDtlsConnect.isInitialized } returns false
        every { mockDtlsConnect.connectionOpenTo("id2") } returns null
        every { mockDtlsConnect.connectionOpenTo("idRelay") } returns null

        mockSending(idRelay, idRelayNsk, RelayCommand.Check::class) { RelayCommand.CheckResponse(1) }
        mockSending(idRelay, idRelayNsk, RelayCommand.TriangleTest1::class) { RelayCommand.TriangleTest3() }
    }

    @Test
    fun `Can send ping`() {
        mockSending(id2, id2nsk, Ping.Ping::class) { Ping.Response() }
        mockSending(idRelay, idRelayNsk, DdbCommand.QueryResolve::class) {
            val query = BaseCommand.fromJson(it[1] as ByteArray) as DdbCommand.QueryResolve
            assertThat(query.id).isEqualTo(id2)
            DdbCommand.QueryResponse(true, AdvertRecord(id2, endpoint2))
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
        mockSending(idRelay, idRelayNsk, DdbCommand.QueryResolve::class) {
            val query = BaseCommand.fromJson(it[1] as ByteArray) as DdbCommand.QueryResolve
            assertThat(query.id).isEqualTo(id2)
            DdbCommand.QueryResponse(true, AdvertRecord(id2, endpoint2))
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

        dtlsParameters.onStunResponse!!.invoke(StunResponse(StunResponseKind.PLAIN, "stun.gmx.net:3478", endpoint1))
        dtlsParameters.onStunResponse!!.invoke(StunResponse(StunResponseKind.PLAIN, "stun.freeswitch.org:3478", endpoint1))

        while(!engine.hasCurrentEndpoint()) {
            Thread.sleep(2000)
        }

        return engine
    }
}