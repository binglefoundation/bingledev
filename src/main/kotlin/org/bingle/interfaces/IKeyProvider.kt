package org.bingle.interfaces

import com.algorand.algosdk.crypto.Address

interface IKeyProvider {
    fun getPrivateKey(): String?
    fun savePrivateKeyObject(address: Address, passphrase: String)
    fun getId(): String?
    fun setId(id: String, passphrase: String)
}
