package org.unknown.comms.interfaces

import com.creatotronik.dtls.IDTLSConnect
import org.unknown.comms.blockchain.AlgoProviderConfig

interface ICommsConfig {
    fun makeKeyProvider(creds: Map<String, String>): IKeyProvider
    // fun makeDTLSConnect(params: DTLSParameters): IDTLSConnect
    fun makeNetworkChangeProvider(): INetworkChangeProvider
    fun makeChainAccess(keyProvider: IKeyProvider): IChainAccess
    fun makeAdvertiser(): IAdvertiser
    fun makeStunResolver(): IStunResolver
    fun makeResolver(): IResolver

    val registerIP: Boolean
    val purestakeApiKey: String?
    val port: Int?
    val localToLoopback: Boolean? // Set this for test to loopback to local machine
    val relay: Boolean? // set this to enable relay
    val forceRelay: Boolean? // become relay without checking NAT
    val disableListener: Boolean? // Disable listening for inbound DTLS
    val useRelays: Boolean? // Use relays if NAT appears to require this
    val alwaysRelayWithId: String? // use this id as a relay always
    val publicEndpoint: String? // Set this to IP/FQDN to use a defined public endpoint rather than discovering with STUN
    val algoProviderConfig: AlgoProviderConfig? // configure how we access Algo blockchain
    val timeouts: TimeoutConfig

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
