package org.bingle.engine.mocks

import com.algorand.algosdk.crypto.Address
import org.bingle.interfaces.IKeyProvider

class MockKeyProvider : IKeyProvider {
    override fun getPrivateKey(): String? {
        TODO("Not yet implemented")
    }

    override fun savePrivateKeyObject(address: Address, passphrase: String) {
        TODO("Not yet implemented")
    }

    override fun getId(): String? = "id1"

    override fun setId(id: String, passphrase: String) {
        TODO("Not yet implemented")
    }

}
