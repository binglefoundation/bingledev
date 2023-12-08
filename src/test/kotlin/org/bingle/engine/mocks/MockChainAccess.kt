package org.bingle.engine.mocks

import com.algorand.algosdk.crypto.Address
import org.bingle.interfaces.IChainAccess
import java.net.InetSocketAddress

class MockChainAccess: IChainAccess {
    private val addressToUsername = mapOf(
        id1 to mockUser1,
        id2 to mockUser2,
        id3 to mockUser3,
        idRelay to mockUserRelay,
    )

    private var relayState: Byte? = null

    override val passphrase: String?
        get() = TODO("Not yet implemented")
    override var address: String?
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun createAddress(b: Boolean, newKey: Boolean): Address {
        TODO("Not yet implemented")
    }

    override fun assetUsername(): String? {
        TODO("Not yet implemented")
    }

    override fun <T, S> retrying(block: (that: S) -> T): T =
        block.invoke(this as S)

    override fun findUsernameByAddress(address: String): String? {
        return addressToUsername[address]
    }

    override fun findIdByUsername(username: String): String? {
        return mapOf(mockUser1 to id1, mockUser2 to id2, mockUser3 to id3, mockUserRelay to idRelay)[username]
    }

    override fun listRelaysWithIps(): List<Pair<String, InetSocketAddress?>> {
        return listOf(Pair(idRelay, endpointRelay))
    }

    override fun registerIP(address: String, ip: InetSocketAddress): Boolean {
        TODO("Not yet implemented")
    }

    override fun listUsers(): List<Pair<String, String>> {
        TODO("Not yet implemented")
    }

    override fun assetBalance(assetId: Long): Double? {
        return 120.0
    }

    override fun setRelayState(relayState: Byte): Boolean {
        this.relayState = relayState
        return true
    }

    override fun privateKeyBytes()= ByteArray(32) { (1 + it).toByte() }

    override fun publicKeyBytes() = ByteArray(32) { (33 - it).toByte() }
}