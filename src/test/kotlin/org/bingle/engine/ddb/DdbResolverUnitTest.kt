package org.bingle.engine.ddb

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.DdbCommand
import org.bingle.command.data.AdvertRecord
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.BaseUnitTest
import org.bingle.engine.IEngineState
import org.bingle.engine.PopulatedRelayInfo
import org.bingle.engine.mocks.endpoint3
import org.bingle.engine.mocks.endpointRelay
import org.bingle.engine.mocks.id3
import org.bingle.engine.mocks.idRelay
import org.junit.jupiter.api.Test

class DdbResolverUnitTest : BaseUnitTest() {

    val mockEngine = mockk<IEngineState>()
    val resolver = DdbResolver(mockEngine)

    init {
        every {
            mockEngine.relayFinder.find()
        } answers {
            PopulatedRelayInfo(idRelay, endpointRelay)
        }
    }

    @Test
    fun `can resolveIdToAdvertRecord`() {
        mockSenderSendToNetworkForResponse(
            mockEngine,
            NetworkSourceKey(endpointRelay),
            idRelay,
            DdbCommand.QueryResolve::class
        )
        {
            val queryCommand = it[2] as DdbCommand.QueryResolve
            assertThat(queryCommand.id).isEqualTo(id3)

            DdbCommand.QueryResponse(true, AdvertRecord(id3, endpoint3))
        }

        val res = resolver.resolveIdToAdvertRecord(id3)
        assertThat(res?.id).isEqualTo(id3)
        assertThat(res?.endpoint).isEqualTo(endpoint3)
    }

    @Test
    fun `can resolveIdToRelay`() {
        mockSenderSendToNetworkForResponse(
            mockEngine,
            NetworkSourceKey(endpointRelay),
            idRelay,
            DdbCommand.QueryResolve::class
        )
        {
            val queryCommand = it[2] as DdbCommand.QueryResolve
            assertThat(queryCommand.id).isEqualTo(idRelay)

            DdbCommand.QueryResponse(true, AdvertRecord(idRelay, endpointRelay))
        }

        val res = resolver.resolveIdToRelay(idRelay)
        assertThat(res?.endpoint).isEqualTo(endpointRelay)
    }
}