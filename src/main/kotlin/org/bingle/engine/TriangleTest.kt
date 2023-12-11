package org.bingle.engine

import org.bingle.annotations.CommandHandler
import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.interfaces.NatType
import org.bingle.interfaces.RegisterAction
import org.bingle.interfaces.ResolveLevel
import org.bingle.util.logDebug
import org.bingle.util.logWarn
import java.net.InetSocketAddress

class TriangleTest(val engine: IEngineState) {

    fun determineNatType(
        resolveLevel: ResolveLevel,
        relayForTriPing: PopulatedRelayInfo,
        endpoint: InetSocketAddress
    ): NatType {
        logDebug("TriangleTest::determineNatType $engine.id - Sending triangleTest1 request")
        engine.config.onState?.invoke(engine.state, resolveLevel, RegisterAction.TRIANGLE_PINGING, null)
        val trianglePingResponse = engine.sender.sendToNetworkForResponse(
            NetworkSourceKey(relayForTriPing.endpoint),
            relayForTriPing.id,
            RelayCommand.TriangleTest1(endpoint),
            engine.config.timeouts.triPing ?: 60000
        )

        if (trianglePingResponse.fail == null) {
            logDebug("Worker::handleStunResponse $engine.id- Got triangleTest response ${trianglePingResponse}")
            engine.config.onState?.invoke(
                engine.state, resolveLevel, RegisterAction.TRIANGLE_PING_RECEIVED,
                NatType.FULL_CONE
            )

            return NatType.FULL_CONE

        } else {
            logDebug("Worker::handleStunResponse $engine.id - triangleTest fail ${trianglePingResponse}, we need relay")
            engine.config.onState?.invoke(
                engine.state, resolveLevel, RegisterAction.TRIANGLE_PING_FAILED,
                NatType.RESTRICTED_CONE
            )
            return NatType.RESTRICTED_CONE
        }
    }
}

@CommandHandler
fun triangleTest1Handler(engineState: IEngineState, command: RelayCommand.TriangleTest1) {
    logDebug("triangleTest1Handler message, find relay to pass on message")
    val relay = engineState.relayFinder.find()
    if (relay == null) {
        logWarn("triangleTest1Handler message: No relay found to pass on triangleTest1")
    } else {
        engineState.sender.sendMessageToNetwork(
            NetworkSourceKey(relay.endpoint),
            relay.id,
            RelayCommand.TriangleTest2(
                command.verifiedId, command.checkingEndpoint,
            ).withTag(command.responseTag),
            null
        )
    }
}

@CommandHandler
fun triangleTest2Handler(engineState: IEngineState, command: RelayCommand.TriangleTest2) {
    // This will be picked up by tag handler in the original sender
    // and return the TriangleTest3 response
    // inside `determineNatType` above
    val networkSourceKey = NetworkSourceKey(command.checkingEndpoint)

    engineState.sender.sendMessageToNetwork(
        networkSourceKey, command.checkingId,
        RelayCommand.TriangleTest3().withTag(command.responseTag),
        null
    )
}
