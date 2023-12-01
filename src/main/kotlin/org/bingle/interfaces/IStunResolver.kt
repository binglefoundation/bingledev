package org.bingle.interfaces

import com.creatotronik.stun.StunResponse
import java.net.InetSocketAddress

enum class ResolveLevel {
    NONE, SINGLE, INCONSISTENT, CONSISTENT
}

interface IStunResolver {

    fun addResponse(stunResponse: StunResponse)
    fun analyse(forcePort: Int? = null): Pair<ResolveLevel, InetSocketAddress?>
}
