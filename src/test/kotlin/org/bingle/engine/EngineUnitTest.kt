package org.bingle.engine

import com.creatotronik.stun.StunResponse
import com.creatotronik.stun.StunResponseKind
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand
import org.bingle.command.DdbCommand
import org.bingle.command.Ping
import org.bingle.command.RelayCommand
import org.bingle.command.data.AdvertRecord
import org.bingle.dtls.DTLSParameters
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.mocks.*
import org.bingle.util.logWarn
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
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

        mockDtlsSend(idRelay, idRelayNsk, RelayCommand.Check::class) { RelayCommand.CheckResponse(1) }
        mockDtlsSend(idRelay, idRelayNsk, RelayCommand.TriangleTest1::class) { RelayCommand.TriangleTest3() }
        mockDtlsSend(
            idRelay,
            idRelayNsk,
            DdbCommand.GetEpoch::class
        ) {
            DdbCommand.GetEpochResponse(1, 1, listOf(idRelay))
        }
    }

    @Test
    fun `Can send ping`() {
        mockDtlsSend(id2, id2nsk, Ping.Ping::class) { Ping.Response() }
        mockDdbQuery(mapOf(id2 to endpoint2))
        mockDdbUpdate()

        mockkStatic(::logWarn.javaMethod!!.declaringClass.kotlin)
        every { logWarn(any()) } returns Unit

        val engine = startEngine()

        val sendRes = engine.sendMessage(mockUser2, Ping.Ping())
        assertThat(sendRes).isTrue()

        engine.stop()

        // This tests that the Pinger gets the response
        verify(exactly = 1) { logWarn("Ping response from unknown sender id1") }
        verifyDdbUpdate()
    }

    @Test
    fun `Responds to ping`() {
        every { mockDtlsConnect.send(id2nsk, any(), any()) } answers {
            val messageSent = BaseCommand.fromJson((it.invocation.args[1]!! as ByteArray).decodeToString())
            assertThat(messageSent).isInstanceOf(Ping.Response::class.java)
            true
        }
        mockDdbQuery(mapOf(id2 to endpoint2))
        mockDdbUpdate()

        val engine = startEngine()

        val message = Ping.Ping().withVerifiedId<Ping.Ping>(id2)
        sendEngineDtlsMessage(id2, id2nsk, message)

        engine.stop()

        verify(exactly = 1) { mockDtlsConnect.send(id2nsk, any(), any()) }
        verifyDdbUpdate()
    }

    @Test
    fun `Can start as initial root relay`() {
        mockCommsConfig = spyk(MockCommsConfig(mockDtlsConnect))
        mockCommsConfig.isRelay = true
        mockCommsConfig.forceRelay = true
        every { mockCommsConfig.makeChainAccess(any()) } returns MockChainAccess(relayInfos=emptyList())

        val engine = startEngine()

        engine.stop()
    }

    @Test
    fun `Can start as second relay`() {
        mockCommsConfig.isRelay = true
        mockCommsConfig.forceRelay = true

        mockDdbQuery(mapOf(idRelay to endpointRelay))

        mockDtlsSend(idRelay, idRelayNsk, DdbCommand.UpsertResolve::class) {
            DdbCommand.UpdateResponse()
        }
        mockDtlsSend<BaseCommand>(idRelay, idRelayNsk, DdbCommand.Signon::class)
        mockDtlsSend(idRelay, idRelayNsk, DdbCommand.InitResolve::class) {
            val dumpResolveMessage = DdbCommand.DumpResolve(idRelay, AdvertRecord(idRelay, endpointRelay))
            Thread {
                Thread.sleep(2000)
                sendEngineDtlsMessage(idRelay, idRelayNsk, dumpResolveMessage)
            }.start()
            DdbCommand.InitResponse(1)
        }

        val engine = startEngine()

        engine.stop()
    }

    private fun sendEngineDtlsMessage(senderId: String, senderNsk: NetworkSourceKey, message: BaseCommand) {
        val messageBytes = message.toJson().toByteArray(Charset.defaultCharset())
        dtlsParameters.onMessage(senderNsk, senderId, messageBytes, messageBytes.size)
    }
    private fun mockDdbUpdate() = mockDtlsSend(idRelay, idRelayNsk, DdbCommand.UpsertResolve::class) {
        val upsert = BaseCommand.fromJson(it[1] as ByteArray) as DdbCommand.UpsertResolve
        assertThat(upsert.record.id).isEqualTo(id1)
        assertThat(upsert.record.endpoint).isEqualTo(endpoint1)
        assertThat(upsert.record.amRelay).isFalse()
        DdbCommand.UpdateResponse()
    }

    private fun verifyDdbUpdate() = verifySending<DdbCommand.UpsertResolve>(idRelayNsk, DdbCommand.UpsertResolve::class)

    private fun mockDdbQuery(endpoints: Map<String, InetSocketAddress>) = mockDtlsSend(idRelay, idRelayNsk, DdbCommand.QueryResolve::class) {
        val query = BaseCommand.fromJson(it[1] as ByteArray) as DdbCommand.QueryResolve
        val endpoint = endpoints[query.id]
        assertThat(endpoint).isNotNull
        DdbCommand.QueryResponse(true, AdvertRecord(query.id, endpoint))
    }

    private fun startEngine(): Engine {
        val engine = Engine(emptyMap(), mockCommsConfig)
        engine.init()

        while (!engine.listening) {
            Thread.sleep(2000)
        }

        dtlsParameters.onStunResponse!!.invoke(StunResponse(StunResponseKind.PLAIN, "stun.gmx.net:3478", endpoint1))
        dtlsParameters.onStunResponse!!.invoke(
            StunResponse(
                StunResponseKind.PLAIN,
                "stun.freeswitch.org:3478",
                endpoint1
            )
        )

        while (!engine.hasCurrentEndpoint()) {
            Thread.sleep(2000)
        }

        return engine
    }
}