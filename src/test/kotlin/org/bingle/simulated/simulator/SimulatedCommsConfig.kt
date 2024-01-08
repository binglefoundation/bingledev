package org.bingle.simulated.simulator

import org.bingle.blockchain.AlgoProviderConfig
import org.bingle.command.TextMessageCommand
import org.bingle.dtls.IDTLSConnect
import org.bingle.engine.IEngineState
import org.bingle.engine.Pingable
import org.bingle.engine.Pinger
import org.bingle.engine.StunResolver
import org.bingle.engine.ddb.DdbAdvertiser
import org.bingle.engine.ddb.DdbResolver
import org.bingle.engine.mocks.MockChainAccess
import org.bingle.engine.mocks.MockKeyProvider
import org.bingle.engine.mocks.MockNetworkChangeProvider
import org.bingle.interfaces.*

class SimulatedCommsConfig(val simulator: Simulator, val node: Simulator.Node) :
    ICommsConfig {
    override fun makeKeyProvider(creds: Map<String, String>): IKeyProvider {
        return SimulatedKeyProvider(node.id)
    }

    override fun makeNetworkChangeProvider(): INetworkChangeProvider {
        return MockNetworkChangeProvider()
    }

    override fun makeChainAccess(keyProvider: IKeyProvider): IChainAccess {
        return SimulatedChainAccess(keyProvider, node.username)
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

    override val registerIP: Boolean = node.relayType === Simulator.RelayType.ROOT_RELAY
    override val purestakeApiKey: String?
        get() = TODO("Not yet implemented")
    override var port: Int? = null

    override val localToLoopback: Boolean? = false
    override var isRelay: Boolean? = node.relayType !== Simulator.RelayType.NOT_RELAY
    override var forceRelay: Boolean? = node.relayType === Simulator.RelayType.ROOT_RELAY
    override val disableListener: Boolean? = false
    override val useRelays: Boolean? = node.relayType === Simulator.RelayType.NOT_RELAY
    override val alwaysRelayWithId: String?
        get() = TODO("Not yet implemented")
    override val publicEndpoint: String? = null
    override val algoProviderConfig: AlgoProviderConfig?
        get() = TODO("Not yet implemented")
    override val timeouts: ICommsConfig.TimeoutConfig = ICommsConfig.TimeoutConfig() // TODO: settings?
    override val onState: CommsStateHandler? = null // TODO
    override val requestPingables: (() -> List<Pingable>)? = null
    override val onAvailability: ((id: String, availability: Pinger.TargetAvailability) -> Unit)? = null // TODO
    override val onUsername: ((id: String, username: String) -> Unit)? = null

    override lateinit var onMessage: (command: TextMessageCommand) -> Unit

    override lateinit var dtlsConnect: IDTLSConnect


}
