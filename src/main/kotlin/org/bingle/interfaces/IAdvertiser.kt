package org.bingle.interfaces

import java.net.InetSocketAddress

interface IAdvertiser {
    fun advertise(keyProvider: IKeyProvider, endpoint: InetSocketAddress)

    fun advertiseUsingRelay(keyProvider: IKeyProvider, relayId: String)
    fun advertiseAmRelay(keyProvider: IKeyProvider, endpoint: InetSocketAddress, relayEndpoint: InetSocketAddress)
}
