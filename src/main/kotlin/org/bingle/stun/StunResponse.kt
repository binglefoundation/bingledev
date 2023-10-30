package com.creatotronik.stun

import java.net.InetSocketAddress

enum class StunResponseKind {
    NONE, PLAIN, XOR, STOP
}
data class StunResponse(val kind: StunResponseKind, val server: String? = null, val myAddress: InetSocketAddress? = null)