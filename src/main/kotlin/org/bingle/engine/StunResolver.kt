package org.bingle.engine

import com.creatotronik.stun.StunResponse
import org.bingle.interfaces.IStunResolver
import org.bingle.interfaces.ResolveLevel
import java.net.InetSocketAddress
import java.util.*

class StunResolver : IStunResolver {
    val responses = mutableMapOf<String, Pair<Date, StunResponse>>()

    val analysers = mutableListOf<() -> Pair<ResolveLevel, InetSocketAddress>?>(
        consistent@{
            val endpoints = HashSet(responses.map { responseAddress(it.value) })
            return@consistent if(endpoints.size == 1 && responses.keys.size > 1) {
                Pair(ResolveLevel.CONSISTENT, endpoints.first())
            } else null
        },
        single@{
            return@single if(responses.keys.size == 1) {
                Pair(ResolveLevel.SINGLE, responseAddress(responses.values.first()))
            } else null
        },
        inconsistent@{
            val endpoints = HashSet(responses.map { responseAddress(it.value) })
            return@inconsistent if(endpoints.size > 1 && responses.keys.size > 1) {
                Pair(ResolveLevel.INCONSISTENT, endpoints.first())
            } else null
        }
    )

    override fun addResponse(stunResponse: StunResponse) {
        this.responses.set(stunResponse.server ?: throw RuntimeException("StunResolver::addResponse null server in response"), Pair(Date(), stunResponse))
    }

    override fun analyse(forcePort: Int?): Pair<ResolveLevel, InetSocketAddress?> {
        val result = analysers.firstNotNullOfOrNull { it.invoke() } ?: Pair(ResolveLevel.NONE, null)
        if(forcePort != null && result.first==ResolveLevel.CONSISTENT) {
            return Pair(result.first, InetSocketAddress(result.second?.address, forcePort))
        }
        return result
    }

    private fun responseAddress(it: Pair<Date, StunResponse>) =
        it.second.myAddress ?: throw RuntimeException("StunResolver::null address in response")

}
