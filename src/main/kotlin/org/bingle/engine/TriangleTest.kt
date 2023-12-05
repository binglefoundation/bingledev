package org.bingle.engine

import org.bingle.command.RelayCommand
import org.bingle.interfaces.NatType
import org.bingle.interfaces.RegisterAction
import org.bingle.interfaces.ResolveLevel
import org.bingle.util.logDebug
import java.net.InetSocketAddress

class TriangleTest(val engine: IEngineState) {

     fun determineNatType(
        resolveLevel: ResolveLevel,
        relayForTriPing: RelayIdToAddress,
        endpoint: InetSocketAddress
    ) : NatType {
        logDebug("TriangleTest::determineNatType $engine.id - Sending triangleTest1 request")
        engine.config.onState?.invoke(engine.state, resolveLevel, RegisterAction.TRIANGLE_PINGING, null)
        val trianglePingResponse = engine.sender.sendToIdForResponse(
            relayForTriPing.first,
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