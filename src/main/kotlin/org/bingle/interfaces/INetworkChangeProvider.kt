package org.bingle.interfaces

interface INetworkChangeProvider {
    fun register(onChange: (hasInternetAccess: Boolean) -> Unit)
    fun stop()
    fun waitForAccess(block: ((hasInternetAccess: Boolean) -> Unit)?)
    fun hasAccess():Boolean
}