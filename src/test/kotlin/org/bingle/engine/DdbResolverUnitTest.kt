package org.bingle.engine

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand
import org.bingle.command.DdbCommand
import org.bingle.command.data.AdvertRecord
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.ddb.DdbResolver
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
            Pair(idRelay, endpointRelay)
        }
    }

    @Test
    fun `can resolveIdToAdvertRecord`() {
        every {
            mockEngine.sender.sendToNetworkForResponse(
                NetworkSourceKey(endpointRelay),
                idRelay,
                any(DdbCommand.QueryResolve::class),
                any()
            )
        } answers {
            val queryCommand = it.invocation.args[2]  as DdbCommand.QueryResolve
            assertThat((queryCommand as DdbCommand.QueryResolve).id).isEqualTo(id3)

            DdbCommand.QueryResponse(true, AdvertRecord(id3, endpoint3))
        }

        val res = resolver.resolveIdToAdvertRecord(id3)
        assertThat(res?.id).isEqualTo(id3)
        assertThat(res?.endpoint).isEqualTo(endpoint3)
    }

    @Test
    fun `can resolveIdToRelay`() {
        every {
            mockEngine.sender.sendToNetworkForResponse(
                NetworkSourceKey(endpointRelay),
                idRelay,
                any(DdbCommand.QueryResolve::class),
                any()
            )
        } answers {
            val queryCommand = it.invocation.args[2]  as DdbCommand.QueryResolve

            assertThat((queryCommand as DdbCommand.QueryResolve).id).isEqualTo(idRelay)

            DdbCommand.QueryResponse(true, AdvertRecord(idRelay, endpointRelay))
        }

        val res = resolver.resolveIdToRelay(idRelay)
        assertThat(res?.endpoint).isEqualTo(endpointRelay)
    }
}