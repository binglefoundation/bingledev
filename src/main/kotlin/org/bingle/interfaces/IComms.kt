package org.bingle.interfaces

import org.bingle.blockchain.RegisterFromPaid
import org.bingle.engine.Pingable
import org.bingle.engine.Pinger
import org.bingle.engine.SignupResponse


enum class RegisterAction {
    USING_PUBLIC,
    NOT_USING_RELAYS,
    FORCE_BE_RELAY,
    RELAY_NOT_AVAILABLE,
    FORCED_USE_RELAY,
    NO_RELAYS_AVAILABLE,
    INCONSISTENT_STUN_RESPONSE,
    AWAIT_MULTIPLE_STUN_RESPONSE,
    TRIANGLE_PING_FAILED,
    TRIANGLE_PINGING,
    TRIANGLE_PING_RECEIVED,
    ADVERTISED_VIA_RELAY,
    ADVERTISED_IS_RELAY,
    ADVERTISED_DIRECT,
    STUN_RESOLVED,
    GOT_ID,
    GOT_ASSET,
    GOT_USER,
    ALGO_NET_FAIL,
    ALGO_HOLDING_FAIL,
    NETWORK_AVAILABLE,
    NO_NETWORK,
    EXCEPTION,
    NO_ID,
    APP_RELOAD
}

enum class NatType {
    NO_NETWORK,
    DIRECT,
    UNKNOWN,
    FULL_CONE,
    SYMMETRIC,
    RESTRICTED_CONE,
}

enum class SendProgress {
    LOOKUP, RESOLVE, SENDING, SENT, FAILED
}

interface ICommsStatus {
    fun isActive(): Boolean
    fun lookupFullHandle(handle: String): String?
    fun listUsers() : List<Pair<String, String>>
}

interface ICommsSender {
    fun currentUser(): Pair<String, String>

    fun sendMessage(
        username: String,
        message: Map<String, Any?>,
        progress: (p: SendProgress, id: String?) -> Unit
    ): Boolean

    fun sendMessageToId(
        userId: String,
        message: Map<String, Any?>,
        progress: ((p: SendProgress, id: String?) -> Unit)? = null
    ): Boolean
}

typealias CommsStateHandler = ((
    commsState: CommsState,
    resolveLevel: ResolveLevel?,
    action: RegisterAction?,
    natType: NatType?,
) -> Unit)

interface ICommsSetup {
    fun signup(newKey: Boolean = false): SignupResponse

    fun registerFromPaid(handle: String): RegisterFromPaid
    fun initComms(
        onMessage: (Map<String, *>) -> Unit,
        onState: CommsStateHandler? = null,
        requestPingables: (() -> List<Pingable>)? = null,
        onAvailability: ((id: String, availability: Pinger.TargetAvailability) -> Unit)? = null,
        onUsername: ((id: String, username: String) -> Unit)? = null
    )

    fun stop()

}

interface IComms : ICommsStatus, ICommsSender, ICommsSetup {
}


