package org.bingle.engine.mocks

import org.bingle.blockchain.AlgoProviderConfig
import org.bingle.command.TextMessageCommand
import org.bingle.dtls.IDTLSConnect
import org.bingle.engine.Pingable
import org.bingle.engine.Pinger
import org.bingle.engine.StunResolver
import org.bingle.interfaces.*
import org.bingle.util.logDebug

class MockCommsConfig(override val dtlsConnect: IDTLSConnect) : ICommsConfig {
    override fun makeKeyProvider(creds: Map<String, String>): IKeyProvider = MockKeyProvider()

    override fun makeNetworkChangeProvider(): INetworkChangeProvider {
        return MockNetworkChangeProvider()
    }

    override fun makeChainAccess(keyProvider: IKeyProvider): IChainAccess {
        return MockChainAccess()
    }

    override fun makeAdvertiser(): IAdvertiser {
        return MockAdvertiser()
    }

    override fun makeStunResolver(): IStunResolver {
        return StunResolver()
    }

    override fun makeResolver(): IResolver {
        return MockResolver()
    }

    override val registerIP: Boolean
        get() = TODO("Not yet implemented")
    override val purestakeApiKey: String?
        get() = TODO("Not yet implemented")
    override val port: Int = 100
    override val localToLoopback: Boolean = false
    override val isRelay: Boolean = false
    override val forceRelay: Boolean?
        get() = TODO("Not yet implemented")
    override val disableListener: Boolean?
        get() = false
    override val useRelays: Boolean?
        get() = TODO("Not yet implemented")
    override val alwaysRelayWithId: String?
        get() = TODO("Not yet implemented")

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