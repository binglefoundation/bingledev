package org.bingle.engine.mocks

import org.bingle.interfaces.IAdvertiser
import org.bingle.interfaces.IKeyProvider
import org.bingle.util.logDebug
import java.net.InetSocketAddress

class MockAdvertiser : IAdvertiser {
    override fun advertise(keyProvider: IKeyProvider, endpoint: InetSocketAddress) {
        logDebug("MockAdvertiser::advertise ${endpoint}")
    }

    override fun advertiseUsingRelay(keyProvider: IKeyProvider, relayId: String) {
        logDebug("MockAdvertiser::advertiseUsingRelay ${relayId}")
    }

    override fun advertiseAmRelay(
        keyProvider: IKeyProvider,
        endpoint: InetSocketAddress,
        relayEndpoint: InetSocketAddress
    ) {
        logDebug("MockAdvertiser::advertiseAmRelay ${endpoint}, ${relayEndpoint}")
    }
}