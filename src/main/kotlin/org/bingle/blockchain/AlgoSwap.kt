package org.bingle.blockchain

import com.algorand.algosdk.builder.transaction.ApplicationCallTransactionBuilder
import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.crypto.Digest
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.transaction.TxGroup

import com.algorand.algosdk.util.Encoder
import org.apache.commons.codec.binary.Base64
import org.bingle.engine.RelayInfo


import org.bingle.interfaces.IKeyProvider
import org.bingle.util.logDebug
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress

data class SwapConfig(var appId: Long, val creatorAddress: String, val assetId: Long)

open class AlgoSwap(val swapConfig: SwapConfig, keyProvider: IKeyProvider?=null, passphrase: String?=null, address: String?=null,
                    algoProviderConfig: AlgoProviderConfig? = null)
    : AlgoOps(keyProvider, passphrase, address, algoProviderConfig) {

    fun swap(amount: Double, tag: String): Boolean {
        // Send a payment, optin and a call operation in a group
        loadAccount()
        val networkClient = connectToNetwork()

        val optinInRes = optInApp(swapConfig.appId)
        if(!optinInRes) {
            logDebug("AlgoSwap Opt in failed")
            return false
        }

        val appArgs = listOf("Swap".toByteArray(), tag.toByteArray())

        val payTxn = Transaction.PaymentTransactionBuilder()
            .sender(myAccount!!.address)
            .amount((1000000 * amount).toInt()) // 1 algo = 1000000 microalgos
            .receiver(Address.forApplication(swapConfig.appId))
            .lookupParams(networkClient)
            .build()

        val optInTxn = Transaction.AssetAcceptTransactionBuilder()
            .acceptingAccount(myAccount!!.address)
            .assetIndex(swapConfig.assetId)
            .lookupParams(networkClient)
            .build()

        val callTxn = ApplicationCallTransactionBuilder.Builder()
            .sender(myAccount!!.address)
            .applicationId(swapConfig.appId)
            .args(appArgs)
            .accounts(listOf(Address(swapConfig.creatorAddress)))
            .lookupParams(networkClient)
            .foreignAssets(listOf(swapConfig.assetId))
            .build()

        val gid: Digest = TxGroup.computeGroupID(payTxn, optInTxn, callTxn)
        payTxn.assignGroupID(gid)
        optInTxn.assignGroupID(gid)
        callTxn.assignGroupID(gid)

        val signedPayTxn = myAccount!!.signTransaction(payTxn)
        val signedOptInTxn = myAccount!!.signTransaction(optInTxn)
        val signedCallTxn = myAccount!!.signTransaction(callTxn)

        // put all transactions in a byte array
        val byteOutputStream = ByteArrayOutputStream()
        val encodedTxBytes1 = Encoder.encodeToMsgPack(signedPayTxn)
        val encodedTxBytes2 = Encoder.encodeToMsgPack(signedOptInTxn)
        val encodedTxBytes3 = Encoder.encodeToMsgPack(signedCallTxn)
        byteOutputStream.write(encodedTxBytes1)
        byteOutputStream.write(encodedTxBytes2)
        byteOutputStream.write(encodedTxBytes3)
        val groupTransactionBytes: ByteArray = byteOutputStream.toByteArray()

        val txResponse = networkClient.RawTransaction().rawtxn(groupTransactionBytes).execute()
        if(!txResponse.isSuccessful) {
            logDebug("AlgoSwap Send grouped failed: ${txResponse.message()}")
            return false
        }

        val txId = txResponse.body().txId
        logDebug("AlgoSwap Successfully sent tx with ID: $txId")

        val confirm = waitForConfirmation(txId, 10)
        logDebug("AlgoSwap Confirmed: $confirm")
        return true
    }

    // Dispense 1 asset
    // Assumes the receiver is opted in to app and asset and has miniumum funds
    fun dispense(receiverAddress: String, tag: String): Boolean {
        loadAccount()
        val networkClient = connectToNetwork()

        val appArgs = listOf("Dispense".toByteArray(), tag.toByteArray())

        var txn = ApplicationCallTransactionBuilder.Builder()
            .sender(myAccount!!.address)
            .applicationId(swapConfig.appId)
            .args(appArgs)
            .accounts(listOf(Address(swapConfig.creatorAddress), Address(receiverAddress)))
            .lookupParams(networkClient)
            .foreignAssets(listOf(swapConfig.assetId))
            .build()

        val signedTx = myAccount!!.signTransaction(txn)
        val appCallResponse = sendSignedTransactionForResponse(signedTx)
        if (appCallResponse == null) return false

        val txId: String = appCallResponse.txId
        println("dispense transaction ID: $txId")

        val confirm = waitForConfirmation(txId, 10)
        println("dispense call confirmed: $confirm")
        return true
    }

    /**
     * set the relay state for the account
     * Check first if we are already in the same state and return false if so
     * Else call the SetRelayState op to set the state
     */
    fun setRelayState(relayState: Byte): Boolean {
        loadAccount()
        val networkClient = connectToNetwork()

        val myLocalState = localState(swapConfig.appId, address!!)
        if(myLocalState?.get("RelayState")?.toInt() ?: 0 == relayState.toInt()) return false

        val appArgs = listOf("SetRelayState".toByteArray(), arrayOf(relayState).toByteArray())

        var txn = ApplicationCallTransactionBuilder.Builder()
            .sender(myAccount!!.address)
            .applicationId(swapConfig.appId)
            .args(appArgs)
            .lookupParams(networkClient)
            .build()

        val signedTx = myAccount!!.signTransaction(txn)
        val appCallResponse = sendSignedTransactionForResponse(signedTx)
        if (appCallResponse == null) return false

        val txId: String = appCallResponse.txId
        println("setRelayState transaction ID: $txId")

        val confirm = waitForConfirmation(txId, 10)
        println("setRelayState call confirmed: $confirm")
        return true
    }


    fun findIdByUsername(username: String): String? {
        return accountsOptedInApp(swapConfig.appId).mapNotNull { account ->
            val addressState = decodeStateForApp(swapConfig.appId, account.appsLocalState)?.let { Pair(account.address.encodeAsString(), it)}

            if(addressState != null) {
                if(addressState.second["AppTag"]?.let { tag -> Base64.decodeBase64(tag).decodeToString() == username } == true) {
                    Pair(addressState.first, addressState.second["AppTagTime"])
                }
                else null
            } else null
        }.sortedBy { it.second }.lastOrNull()?.first
    }

    fun findUsernameByAddress(address: String): String? {
        val localState = localState(swapConfig.appId, address)
        return localState?.get("AppTag")?.let { Base64.decodeBase64(it).decodeToString() }
    }

    fun assetUsername(): String? {
        return findUsernameByAddress(address!!)
    }

    fun listUsers(): List<Pair<String, String>> {
        return localState(swapConfig.appId).mapNotNull {
            it.value["AppTag"]?.let { tag ->
                val res = Pair(it.key, Base64.decodeBase64(tag).decodeToString())
                logDebug("AlgoSwap listUsers finds ${res} in localState")
                res
            }
        }
    }

    fun listRelaysWithIps(): List<RelayInfo> {
        // TODO: determine (from global) if we have a root relay and the IP address
        return localState(swapConfig.appId).mapNotNull {
            if((it.value["RelayState"]?.toInt() ?: 0) > 0 ) RelayInfo(it.key) else null
        }
    }

    fun registerIP(address: String, ip: InetSocketAddress): Boolean {
        throw NotImplementedError("registerIP needs to be implemented")
    }
}