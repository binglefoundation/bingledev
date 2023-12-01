package org.bingle.engine.mocks

import org.bingle.interfaces.INetworkChangeProvider

class MockNetworkChangeProvider : INetworkChangeProvider {
    override fun register(onChange: (hasInternetAccess: Boolean) -> Unit) {

    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun waitForAccess(block: ((hasInternetAccess: Boolean) -> Unit)?) {
        return
    }

    override fun hasAccess(): Boolean {
        return true
    }

}
