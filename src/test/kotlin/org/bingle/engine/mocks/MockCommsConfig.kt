package org.bingle.engine.mocks

import org.bingle.blockchain.AlgoProviderConfig
import org.bingle.command.TextMessageCommand
import org.bingle.dtls.IDTLSConnect
import org.bingle.engine.*
import org.bingle.engine.ddb.DdbAdvertiser
import org.bingle.engine.ddb.DdbResolver
import org.bingle.interfaces.*
import org.bingle.util.logDebug

class MockCommsConfig(override val dtlsConnect: IDTLSConnect,
                      override val registerIP: Boolean=false
) : ICommsConfig {
    override fun makeKeyProvider(creds: Map<String, String>): IKeyProvider = MockKeyProvider()

    override fun makeNetworkChangeProvider(): INetworkChangeProvider {
        return MockNetworkChangeProvider()
    }

    override fun makeChainAccess(keyProvider: IKeyProvider): IChainAccess {
        return MockChainAccess(relayInfos = listOf(RelayInfo(idRelay, endpointRelay, true)))
    }

    override fun makeAdvertiser(engineState: IEngineState): IAdvertiser {
        return DdbAdvertiser(engineState)
    }

    override fun makeStunResolver(): IStunResolver {
        return StunResolver()
    }

    override fun makeResolver(engineState: IEngineState): IResolver {
        return DdbResolver(engineState)
    }

    override val purestakeApiKey: String?
        get() = TODO("Not yet implemented")
    override val port: Int = 100
    override val localToLoopback: Boolean = false
    override var isRelay: Boolean? = false
    override var forceRelay: Boolean? = false
    override val disableListener: Boolean?
        get() = false
    override val useRelays: Boolean?
        get() = true
    override val alwaysRelayWithId: String?
        get() = null

    override val publicEndpoint: String? = null
    override val algoProviderConfig: AlgoProviderConfig?
        get() = TODO("Not yet implemented")
    override val timeouts: ICommsConfig.TimeoutConfig = ICommsConfig.TimeoutConfig()

    override val onState: CommsStateHandler? = null
    override val requestPingables: (() -> List<Pingable>)?
        get() = {  emptyList() }
    override val onAvailability: ((id: String, availability: Pinger.TargetAvailability) -> Unit)? = null
    override val onUsername: ((id: String, username: String) -> Unit)? = null
    override var onMessage: (command: TextMessageCommand) -> Unit =  {
        logDebug("MockCommsConfig::onMessage ${it}")
    }
}