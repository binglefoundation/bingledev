package org.bingle.interfaces

import com.algorand.algosdk.crypto.Address
import org.bingle.blockchain.generated.AlgoConfig
import java.net.InetSocketAddress

interface IChainAccess {
    val passphrase: String?
    var address: String?

    fun createAddress(b: Boolean, newKey: Boolean): Address
    fun assetUsername(): String?
    fun <T, S> retrying(block: (that: S) -> T): T

    fun findUsernameByAddress(address: String): String?
    fun findIdByUsername(username: String): String?

    fun listRelaysWithIps(): List<Pair<String, InetSocketAddress?>>
    fun registerIP(address: String, ip: InetSocketAddress): Boolean

    fun listUsers(): List<Pair<String, String>>

    fun assetBalance(assetId: Long = AlgoConfig.assetId): Double?
    fun setRelayState(relayState: Byte): Boolean
    fun privateKeyBytes(): ByteArray
    fun publicKeyBytes(): ByteArray

}