package org.bingle.engine.mocks

import org.bingle.interfaces.IAdvertiser
import org.bingle.interfaces.IKeyProvider
import java.net.InetSocketAddress

class MockAdvertiser : IAdvertiser {
    override fun advertise(keyProvider: IKeyProvider, endpoint: InetSocketAddress) {
        TODO("Not yet implemented")
    }

    override fun advertiseUsingRelay(keyProvider: IKeyProvider, relayId: String) {
        TODO("Not yet implemented")
    }

    override fun advertiseAmRelay(
        keyProvider: IKeyProvider,
        endpoint: InetSocketAddress,
        relayEndpoint: InetSocketAddress
    ) {
        TODO("Not yet implemented")
    }
}