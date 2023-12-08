package org.bingle.engine

import org.bingle.command.RelayCommand
import org.bingle.interfaces.CommsState
import org.bingle.interfaces.NatType
import org.bingle.interfaces.RegisterAction
import org.bingle.interfaces.ResolveLevel
import org.bingle.util.logDebug
import org.bingle.util.logError
import java.net.InetSocketAddress

class Relay(val engine: IEngineState) {

    fun adoptRelayState(
        endpoint: InetSocketAddress,
        resolveLevel: ResolveLevel
    ) {
        if (engine.config.useRelays == false || engine.config.forceRelay == true) {
            engine.currentEndpoint = endpoint
            engine.config.onState?.invoke(
                engine.state,
                resolveLevel,
                if (engine.config.useRelays == false) RegisterAction.NOT_USING_RELAYS else RegisterAction.FORCE_BE_RELAY,
                NatType.UNKNOWN
            )

            advertiseResolution(endpoint, resolveLevel, NatType.DIRECT)
            logDebug("Relay::adoptRelayState (${if (engine.config.useRelays == false) "not using relays" else "force to be relay"}) assign current endpoint from Stun as ${endpoint}")

            if (engine.config.isRelay == true && engine.config.forceRelay == true) {
                // Forced relay, normally direct
                engine.worker.initDDBApp(endpoint) // TODO: this changes to its own thing
                advertiseAmRelay(endpoint, ResolveLevel.CONSISTENT)
            }
        } else if (engine.config.alwaysRelayWithId != null) {
            logDebug("Relay::InitComms (always relay) assign current endpoint from Stun as ${endpoint}")
            engine.currentEndpoint = endpoint

            becomeRelayClient(resolveLevel, true)
        } else if (resolveLevel == ResolveLevel.INCONSISTENT) {
            logDebug("Relay::InitComms (inconsistent Stun responses) assign current endpoint from Stun as ${endpoint}")
            // engine.currentEndpoint = endpoint // ??

            becomeRelayClient(resolveLevel, false, NatType.SYMMETRIC)
        } else {
            logDebug("Relay::adoptRelayState - $engine.id finds relay for TriPing")
            val relayForTriPing = engine.relayFinder.find()
            logDebug("Relay::adoptRelayState - $engine.id triangleTest relay ${relayForTriPing}")
            if (relayForTriPing != null) {
                val environmentNatType = engine.triangleTest.determineNatType(resolveLevel, relayForTriPing, endpoint)
                when(environmentNatType) {
                    NatType.RESTRICTED_CONE -> {
                        sendRelayListen(relayForTriPing.first, relayForTriPing.second)
                        advertiseUsingRelay(relayForTriPing.first, NatType.RESTRICTED_CONE)
                    }
                    NatType.FULL_CONE -> {
                        engine.relay.advertiseResolution(endpoint, resolveLevel, NatType.FULL_CONE)
                        engine.currentEndpoint = endpoint

                        if (engine.config.isRelay == true && engine.config.forceRelay != true) {
                            // relay on a full cone NAT
                            engine.worker.initDDBApp(endpoint)
                            engine.relay.advertiseAmRelay(endpoint, resolveLevel)
                        }
                    }
                    else ->
                        logError("Relay::adoptRelayState - Unexpected NatType ${environmentNatType} from triangle test")
                }
            } else {
                logError("Relay::adoptRelayState - No relays available")
                engine.state = CommsState.FAILED
                engine.config.onState?.invoke(
                    engine.state, resolveLevel, RegisterAction.NO_RELAYS_AVAILABLE,
                    NatType.UNKNOWN
                )
            }
        }
    }
    fun adoptRelayForPublicEndpoint() {
        if (engine.config.isRelay == true && engine.config.forceRelay == true) {
            logDebug("Relay::adoptRelayForPublicEndpoint am relay on public endpoint")
            engine.worker.initDDBApp(engine.currentEndpoint)
            advertiseAmRelay(engine.currentEndpoint, ResolveLevel.CONSISTENT)
        } else {
            advertiseResolution(
                engine.currentEndpoint,
                ResolveLevel.CONSISTENT,
                NatType.DIRECT
            )
        }
    }

    fun advertiseAmRelay(
        endpoint: InetSocketAddress,
        resolveLevel: ResolveLevel
    ) {
        logDebug("Relay::advertiseAmRelay ${endpoint}")
        engine.advertiser.advertiseAmRelay(engine.keyProvider, engine.currentEndpoint, endpoint)

        engine.state = CommsState.RELAY_ADVERTISED
        engine.config.onState?.invoke(engine.state, resolveLevel, RegisterAction.ADVERTISED_IS_RELAY, null)
    }

    fun advertiseResolution(
        endpoint: InetSocketAddress,
        resolveLevel: ResolveLevel,
        natType: NatType
    ) {
        logDebug("Relay::advertiseResolution ${endpoint}")
        engine.advertiser.advertise(
            engine.keyProvider,
            endpoint
        )
        engine.state = CommsState.ADVERTISED
        engine.config.onState?.invoke(engine.state, resolveLevel, RegisterAction.ADVERTISED_DIRECT, natType)
    }

    private fun becomeRelayClient(
        resolveLevel: ResolveLevel,
        forced: Boolean,
        natType: NatType = NatType.UNKNOWN
    ) {
        logDebug("Relay::becomeRelayClient finds relay")
        val relayToUse = engine.relayFinder.find() // will honour alwaysRelayWithId
        if (relayToUse == null) {
            logError("Comms:onStunResponse - relay ${engine.config.alwaysRelayWithId} is not available")
            engine.state = CommsState.FAILED
            engine.config.onState?.invoke(
                engine.state,
                resolveLevel,
                if (engine.config.alwaysRelayWithId != null) RegisterAction.RELAY_NOT_AVAILABLE else RegisterAction.NO_RELAYS_AVAILABLE,
                natType
            )
        } else {
            if (forced) {
                engine.config.onState?.invoke(
                    engine.state, resolveLevel, RegisterAction.FORCED_USE_RELAY,
                    natType
                )
            }

            if (sendRelayListen(relayToUse.first, relayToUse.second)) {
                advertiseUsingRelay(relayToUse.first, natType)
            } else {
                logError("Comms:onStunResponse - relay ${engine.config.alwaysRelayWithId} did not respond to listen")
                engine.state = CommsState.FAILED
                engine.config.onState?.invoke(
                    engine.state,
                    resolveLevel,
                    if (engine.config.alwaysRelayWithId != null) RegisterAction.RELAY_NOT_AVAILABLE else RegisterAction.NO_RELAYS_AVAILABLE,
                    natType
                )
            }
        }
    }

    fun sendRelayListen(relayId: String, relayAddress: InetSocketAddress): Boolean {
        // config.dtlsConnect!!.sendRelayPing(relayAddress)

        val res = engine.sender.sendToIdForResponse(
            relayId,
            RelayCommand.Listen(),
            engine.config.timeouts.relayListen,
        )

        return null == res.fail
    }

    fun advertiseUsingRelay(
        relayId: String,
        natType: NatType
    ) {
        logDebug("Relay::advertiseUsingRelay id ${relayId}")
        engine.advertiser.advertiseUsingRelay(engine.keyProvider, relayId)

        engine.state = CommsState.ADVERTISED
        engine.config.onState?.invoke(
            engine.state,
            ResolveLevel.NONE,
            RegisterAction.ADVERTISED_VIA_RELAY,
            natType
        )
    }
}