package org.bingle.simulated.simulator

import com.algorand.algosdk.crypto.Address
import org.bingle.blockchain.AlgoAssetAccess
import org.bingle.engine.RelayInfo
import org.bingle.interfaces.IChainAccess
import org.bingle.interfaces.IKeyProvider
import java.net.InetSocketAddress

class SimulatedChainAccess(keyProvider: IKeyProvider, val username: String) : IChainAccess {
    val algoChainAccess = AlgoAssetAccess(keyProvider)

    init {
        addressToUsername.put( algoChainAccess.address!!, username)
    }

    override val passphrase: String?
        get() = algoChainAccess.passphrase

    override var address: String?
        get() = algoChainAccess.address
        set(value) {
            TODO("Cant reset address")
        }

    override fun createAddress(b: Boolean, newKey: Boolean): Address {
        return algoChainAccess.createAddress(b, newKey)
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
        TODO("Not yet implemented")
    }

    override fun listRelaysWithIps(): List<RelayInfo> {
        TODO("Not yet implemented")
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
        return true // TODO: we should emulate fully?
    }

    override fun privateKeyBytes(): ByteArray {
        return algoChainAccess.privateKeyBytes()
    }

    override fun publicKeyBytes(): ByteArray {
        return algoChainAccess.publicKeyBytes()
    }

    companion object {
        val addressToUsername = mutableMapOf<String, String>()
    }
}