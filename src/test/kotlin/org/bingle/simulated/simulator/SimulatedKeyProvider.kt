package org.bingle.simulated.simulator

import com.algorand.algosdk.crypto.Address
import org.bingle.blockchain.AlgoOps
import org.bingle.interfaces.IKeyProvider

class SimulatedKeyProvider(val myId: String) : IKeyProvider {

    val algoOps = AlgoOps()

    init {
        algoOps.createAddress(false, true)
    }

    override fun getPrivateKey(): String? {
        return algoOps.passphrase
    }

    override fun savePrivateKeyObject(address: Address, passphrase: String) {
        TODO("Not yet implemented")
    }

    override fun getId(): String {
        return algoOps.address!!
    }

    override fun setId(id: String, passphrase: String) {
        TODO("Not yet implemented")
    }

}
