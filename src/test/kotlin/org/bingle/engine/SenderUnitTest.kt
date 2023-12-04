package org.bingle.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand.Companion.klaxonParser
import org.bingle.command.RelayCommand
import org.bingle.command.ResponseCommand
import org.bingle.command.TextMessageCommand
import org.bingle.dtls.IDTLSConnect
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.mocks.*
import org.bingle.interfaces.SendProgress
import org.bingle.util.logDebug
import org.junit.jupiter.api.Test

class SenderUnitTest : BaseUnitTest() {

    @Test
    fun `SendMessage can send a message addressed by username`() {
        val mockDtlsConnect = mockk<IDTLSConnect>()
        val id2nsk = NetworkSourceKey(endpoint2)
        every { mockDtlsConnect.connectionOpenTo("id2") } returns id2nsk
        every { mockDtlsConnect.send(id2nsk, any(), any()) } returns true

        val mockEngine = MockEngine(mockDtlsConnect)
        val sender = Sender(mockEngine)

        val progress = mockk<(p: SendProgress, id: String?) -> Unit>(relaxed = true)

        assertThat(sender.sendMessage(mockUser2, TextMessageCommand("Hello"), progress)).isTrue()

        verify {
            mockDtlsConnect.send(id2nsk, any(), any())
        }
    }

    @Test
    fun `SendMessage can send a message addressed by username via relay`() {
        val mockDtlsConnect = mockk<IDTLSConnect>()
        val relayNsk = NetworkSourceKey(endpointRelay)
        every { mockDtlsConnect.connectionOpenTo("id3") } returns null // will never open
        every { mockDtlsConnect.connectionOpenTo("idRelay") } returns null
        every { mockDtlsConnect.send(relayNsk, any(), any()) } returns true
        val relayTurnNSK = NetworkSourceKey(endpointRelay, 10)
        every { mockDtlsConnect.send(relayTurnNSK, any(), any()) } answers {
            val messageMap = klaxonParser().parse<Map<String, Any>>((it.invocation.args[1]!! as ByteArray).decodeToString())
            assertThat(messageMap?.get("text")).isEqualTo("Bonjour")
            true
        }

        var mockEngine: IEngineState? = null
        every { mockDtlsConnect.send(relayNsk, any(), any()) } answers {
            val messageMap = klaxonParser().parse<Map<String, Any>>((it.invocation.args[1]!! as ByteArray).decodeToString())
            val tag = messageMap!!["responseTag"]
            logDebug("send to idRelay with tag ${tag}")
            assertThat(messageMap["type"]).isEqualTo("relay_command.call")
            assertThat(messageMap["calledId"]).isEqualTo(id3)
            mockEngine!!.responseSlots[tag]!!.msg = RelayCommand.RelayResponse( 10)
            mockEngine!!.responseSlots[tag]!!.latch.countDown()
            true
        }

        mockEngine = MockEngine(mockDtlsConnect)
        mockEngine.listening = true

        val sender = Sender(mockEngine)

        val progress = mockk<(p: SendProgress, id: String?) -> Unit>(relaxed = true)

        assertThat(sender.sendMessage(mockUser3, TextMessageCommand("Bonjour"), progress)).isTrue()

        verify {
            mockDtlsConnect.send(relayNsk, any(), any())
            mockDtlsConnect.send(relayTurnNSK, any(), any())
        }
    }

    @Test
    fun `SendMessageToId can send a message addressed by id when connection open`() {
        val mockDtlsConnect = mockk<IDTLSConnect>()
        val id2nsk = NetworkSourceKey(endpoint2)
        every { mockDtlsConnect.connectionOpenTo("id2") } returns id2nsk
        every { mockDtlsConnect.send(id2nsk, any(), any()) } returns true

        val mockEngine = MockEngine(mockDtlsConnect)
        val sender = Sender(mockEngine)

        val progress = mockk<(p: SendProgress, id: String?) -> Unit>(relaxed = true)

        assertThat(sender.sendMessageToId(id2, TextMessageCommand("Kia Ora"), progress)).isTrue()

        verify {
            mockDtlsConnect.send(id2nsk, any(), any())
        }
    }

    @Test
    fun `SendMessageToId can send a message addressed by id making new connection`() {
        val mockDtlsConnect = mockk<IDTLSConnect>()
        val id2nsk = NetworkSourceKey(endpoint2)
        every { mockDtlsConnect.connectionOpenTo("id2") } returns null
        every { mockDtlsConnect.send(id2nsk, any(), any()) } returns true

        val mockEngine = MockEngine(mockDtlsConnect)
        val sender = Sender(mockEngine)

        val progress = mockk<(p: SendProgress, id: String?) -> Unit>(relaxed = true)

        assertThat(sender.sendMessageToId(id2, TextMessageCommand("Ciao"), progress)).isTrue()

        verify {
            mockDtlsConnect.send(id2nsk, any(), any())
        }
    }

    @Test
    fun `sendToIdForResponse can send a message addressed by username and await response`() {
        val mockDtlsConnect = mockk<IDTLSConnect>()
        val id2nsk = NetworkSourceKey(endpoint2)
        every { mockDtlsConnect.connectionOpenTo("id2") } returns id2nsk

        var mockEngine: IEngineState? = null
        every { mockDtlsConnect.send(id2nsk, any(), any()) } answers {
            val tag = klaxonParser().parse<Map<String, Any>>((it.invocation.args[1]!! as ByteArray).decodeToString())!!["responseTag"]
            logDebug("send to id2 with tag ${tag}")
            mockEngine!!.responseSlots[tag]!!.msg = ResponseCommand("ok")
            mockEngine!!.responseSlots[tag]!!.latch.countDown()
            true
        }

        mockEngine = MockEngine(mockDtlsConnect)
        mockEngine.listening = true

        val sender = Sender(mockEngine)

        val response = sender.sendToIdForResponse(id2, TextMessageCommand("G'Day"), null)
        assertThat(response).isEqualTo(ResponseCommand("ok"))

        verify {
            mockDtlsConnect.send(id2nsk, any(), any())
        }
    }
}