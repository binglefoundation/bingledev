package org.bingle.engine.ddb

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.bingle.command.DdbCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.BaseUnitTest
import org.bingle.engine.IEngineState
import org.bingle.engine.PopulatedRelayInfo
import org.bingle.engine.mocks.endpoint1
import org.bingle.engine.mocks.endpointRelay
import org.bingle.engine.mocks.id1
import org.bingle.engine.mocks.idRelay
import org.bingle.interfaces.IKeyProvider
import org.junit.jupiter.api.Test

class DdbAdvertiserUnitTest : BaseUnitTest() {
    val mockEngine = mockk<IEngineState>()
    val advertiser = DdbAdvertiser(mockEngine)
    val mockKeyProvider = mockk<IKeyProvider>()

    init {
        every { mockEngine.keyProvider } returns mockKeyProvider
        every { mockKeyProvider.getId() } returns id1
        every {
            mockEngine.relayFinder.find()
        } answers {
            PopulatedRelayInfo(idRelay, endpointRelay)
        }
    }

    @Test
    fun `advertise updates with an endpoint advert`() {
        mockSenderSendToNetworkForResponse(
            mockEngine,
            NetworkSourceKey(endpointRelay),
            idRelay,
            DdbCommand.UpsertResolve::class
        )
        {
            val upsertCommand = it[2] as DdbCommand.UpsertResolve
            assertThat(upsertCommand.updateId).isEqualTo(id1)
            assertThat(upsertCommand.record.id).isEqualTo(id1)
            assertThat(upsertCommand.record.endpoint).isEqualTo(endpoint1)

            DdbCommand.UpdateResponse()
        }

        advertiser.advertise(mockEngine.keyProvider, endpoint1)
    }

    @Test
    fun `advertiseUsingRelay updates with a relay advert`() {
        mockSenderSendToNetworkForResponse(
            mockEngine,
            NetworkSourceKey(endpointRelay),
            idRelay,
            DdbCommand.UpsertResolve::class
        )
        {
            val upsertCommand = it[2] as DdbCommand.UpsertResolve
            assertThat(upsertCommand.updateId).isEqualTo(id1)
            assertThat(upsertCommand.record.id).isEqualTo(id1)
            assertThat(upsertCommand.record.relayId).isEqualTo(idRelay)

            DdbCommand.UpdateResponse()
        }

        advertiser.advertiseUsingRelay(mockEngine.keyProvider, idRelay)
    }
    @Test
    fun `advertiseAmRelay updates with relay info`() {
        mockSenderSendToNetworkForResponse(
            mockEngine,
            NetworkSourceKey(endpointRelay),
            idRelay,
            DdbCommand.UpsertResolve::class
        )
        {
            val upsertCommand = it[2] as DdbCommand.UpsertResolve
            assertThat(upsertCommand.updateId).isEqualTo(id1)
            assertThat(upsertCommand.record.id).isEqualTo(id1)
            assertThat(upsertCommand.record.endpoint).isEqualTo(endpointRelay)
            assertThat(upsertCommand.record.amRelay).isTrue()

            DdbCommand.UpdateResponse()
        }

        advertiser.advertiseAmRelay(mockEngine.keyProvider, relayEndpoint = endpointRelay, endpoint = endpointRelay)
    }

}