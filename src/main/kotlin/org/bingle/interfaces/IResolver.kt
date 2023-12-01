package org.bingle.interfaces

import org.bingle.command.data.AdvertRecord
import java.net.InetSocketAddress
import java.util.*

interface IResolver {
    data class RelayDns(val endpoint: InetSocketAddress, val updated: Date)

    fun resolveIdToRelay(id: String): RelayDns?
    fun resolveIdToAdvertRecord(id: String): AdvertRecord?
}