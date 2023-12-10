package org.bingle.interfaces

import org.bingle.blockchain.AlgoProviderConfig
import org.bingle.command.TextMessageCommand
import org.bingle.dtls.IDTLSConnect
import org.bingle.engine.IEngineState
import org.bingle.engine.Pingable
import org.bingle.engine.Pinger


interface ICommsConfig {
    fun makeKeyProvider(creds: Map<String, String>): IKeyProvider
    // fun makeDTLSConnect(params: DTLSParameters): IDTLSConnect
    fun makeNetworkChangeProvider(): INetworkChangeProvider
    fun makeChainAccess(keyProvider: IKeyProvider): IChainAccess
    fun makeAdvertiser(engineState: IEngineState): IAdvertiser
    fun makeStunResolver(): IStunResolver
    fun makeResolver(engineState: IEngineState): IResolver

    val registerIP: Boolean
    val purestakeApiKey: String?
    val port: Int?
    val localToLoopback: Boolean? // Set this for test to loopback to local machine
    val isRelay: Boolean? // set this to enable becoming a relay
    val forceRelay: Boolean? // become relay without checking NAT
    val disableListener: Boolean? // Disable listening for inbound DTLS
    val useRelays: Boolean? // Use relays if NAT appears to require this
    val alwaysRelayWithId: String? // use this id as a relay always
    val publicEndpoint: String? // Set this to IP/FQDN to use a defined public endpoint rather than discovering with STUN
    val algoProviderConfig: AlgoProviderConfig? // configure how we access Algo blockchain
    val timeouts: TimeoutConfig
    val onState: CommsStateHandler?
    val requestPingables: (() -> List<Pingable>)?
    val onAvailability: ((id: String, availability: Pinger.TargetAvailability) -> Unit)?
    val onUsername: ((id: String, username: String) -> Unit)?
    // var onMessage: (decodedMessage: MutableMap<String, Any?>) -> Unit
    var onMessage: (command: TextMessageCommand) -> Unit

    data class TimeoutConfig(
        val handshake: Int? = null,
        val dtlsPacketReceive: Int? = null,
        val verify: Int? = null,
        val triPing: Long? = null,
        val relayListen: Long? = null,
        val relayCheck: Long? = null,
    )

    val dtlsConnect: IDTLSConnect
}
