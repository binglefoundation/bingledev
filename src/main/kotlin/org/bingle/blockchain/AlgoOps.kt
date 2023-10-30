package org.unknown.comms.blockchain

import com.algorand.algosdk.account.Account
import com.algorand.algosdk.builder.transaction.*
import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.crypto.Signature
import com.algorand.algosdk.crypto.TEALProgram
import com.algorand.algosdk.logic.StateSchema
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.algod.TealCompile
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.common.IndexerClient
import com.algorand.algosdk.v2.client.common.Response
import com.algorand.algosdk.v2.client.indexer.SearchForTransactions
import com.algorand.algosdk.v2.client.model.*
import com.creatotronik.util.logDebug
import org.apache.commons.codec.binary.Base32
import org.spongycastle.util.encoders.Base64
import org.unknown.comms.blockchain.generated.AlgoConfig
import org.unknown.comms.interfaces.IChainAccess
import org.unknown.comms.interfaces.IKeyProvider
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

const val TOKEN = "mtLlcUOiQjaEaXzS9Owul296tJLZS8WT310WTE9N"    // For purestake

data class AlgoProviderConfig(
    val clientApiUrl: String,
    val clientApiPort: Int,
    val indexerApiUrl: String,
    val indexerApiPort: Int,
    val token: String)

open class AlgoOps(
    private val keyProvider: IKeyProvider? = null, var passphrase: String? = null, var address: String? = null,
    var algoProviderConfig: AlgoProviderConfig? = null
) {
    data class SenderResult(val success: Boolean, val fee: Double? = null)
    data class BlockCacheEntry<R>(var data: List<R>, var round: Long)

    val blockCache: MutableMap<String, BlockCacheEntry<*>> = mutableMapOf()

    var myAccount: Account? = null

    init {
        if(algoProviderConfig==null) algoProviderConfig = AlgoProviderConfig(
            "https://testnet-algorand.api.purestake.io/ps2",
            443,
            "https://testnet-algorand.api.purestake.io/idx2",
            443,
            TOKEN
        )

        if (keyProvider != null && address == null) address = keyProvider.getId()
        if (passphrase == null) passphrase = keyProvider?.getPrivateKey()

        if (address == null) {
            if (passphrase != null) accountFromPassphrase()
        }
    }

    fun createAddress(save: Boolean, alwaysNewAddress: Boolean = false): Address {
        if (keyProvider?.getPrivateKey() != null && !alwaysNewAddress) throw RuntimeException("createAddress when we already have one")

        myAccount = Account()
        passphrase = myAccount!!.toMnemonic()
        logDebug("val passphrase = \"$passphrase\"")
        logDebug("val address = \"${myAccount!!.address}\"")
        if (save && keyProvider != null) {
            keyProvider.savePrivateKeyObject(myAccount!!.address, passphrase!!)
        } else {
            logDebug("Did not save key, save=${save}, keyProvider=${keyProvider}")
        }
        address = myAccount!!.address.encodeAsString()
        return myAccount!!.address
    }

    fun publicKeyBytes(): ByteArray {
        loadAddress()
        return Base32().decode(address).slice(0..31).toByteArray()
    }

    fun privateKeyBytes(): ByteArray {
        loadAccount()
        return myAccount!!.toSeed()
    }

    fun contractAddress(appId: Long): String = Address.forApplication(appId).encodeAsString()

    fun sign(text: String): String {
        loadAccount()

        return Base64.encode(myAccount!!.signBytes(text.toByteArray()).bytes).toString(Charset.defaultCharset())
    }

    fun verify(text: String, sig: String): Boolean {
        loadAddress()

        return Address(address).verifyBytes(text.toByteArray(), Signature(Base64.decode(sig)))
    }

    private fun loadAddress() {
        if (address == null) throw RuntimeException("This operation needs an address")
    }

    fun loadAccount() {
        if (myAccount != null) return

        if (passphrase == null) {
            passphrase = keyProvider?.getPrivateKey()
        }

        if (null == passphrase) throw RuntimeException("This operation needs account access")
        accountFromPassphrase()
    }

    private fun accountFromPassphrase() {
        myAccount = Account(passphrase)
        address = myAccount!!.address.encodeAsString()
    }

    fun accountBalance(): Double? {
        val accountInfoResponse = fetchAccountInfo()

        return accountInfoResponse?.body()?.amount?.let {
            return it.toDouble() / 1_000_000
        }
    }

    fun globalState(appId: Long?): Map<Long, Map<String, String>>? {
        return forApplications(appId) { app ->
            app.id to app.params.globalState.associate {
                Base64.decode(it.key).decodeToString() to
                        when (it.value.type) {
                            1L -> it.value.bytes
                            else -> it.value.uint.toString()
                        }
            }
        }
    }

    inline fun localState(appId: Long, block: (Pair<String, Map<String, String>>) -> Unit) {
        logDebug("AlgoOps localState (${appId}, block) starts")

        accountsOptedInApp(appId).map { account ->
            val addressState =
                decodeStateForApp(appId, account.appsLocalState)?.let { Pair(account.address.encodeAsString(), it) }

            if (addressState != null) block(addressState)
        }
    }

    fun localState(appId: Long): Map<String, Map<String, String>> {
        logDebug("AlgoOps localState (${appId}) starts")

        val res = accountsOptedInApp(appId).mapNotNull { account ->
            decodeStateForApp(appId, account.appsLocalState)?.let { Pair(account.address.encodeAsString(), it) }
            // localState(appId, accountAddress)?.let { Pair(accountAddress, it) }
        }.toMap()

        logDebug("AlgoOps localState (${appId}) done, ${res.size} entries")

        return res
    }

    fun localState(
        appId: Long,
        accountAddress: String
    ): Map<String, String>? {
        logDebug("AlgoOps localState (${appId}, ${accountAddress}) starts")
        val networkClient = connectToNetwork()

        val accountInfoResponse =
            retryingRequest { networkClient.AccountInformation(Address(accountAddress)).execute() }
        if (!accountInfoResponse.isSuccessful) {
            throw AlgoNetworkException("localState account $accountAddress returned no info")
        }

        val appsLocalState = accountInfoResponse.body().appsLocalState
        return decodeStateForApp(appId, appsLocalState)
    }

    fun decodeStateForApp(
        appId: Long,
        appsLocalState: MutableList<ApplicationLocalState>
    ) = appsLocalState
        .find { it.id == appId }
        ?.keyValue
        ?.associate {
            Base64.decode(it.key).decodeToString() to
                    when (it.value.type) {
                        1L -> it.value.bytes
                        else -> it.value.uint.toString()
                    }
        }

    fun accountsOptedInApp(appId: Long): List<com.algorand.algosdk.v2.client.model.Account> {
        val srchRes = retryingRequest { connectToIndexer().searchForAccounts().applicationId(appId).execute() }
        if (!srchRes.isSuccessful) {
            logDebug("accountsOptedInApp search failed ${srchRes.message()}")
            return emptyList<com.algorand.algosdk.v2.client.model.Account>()
        }
        val accountsResponse = srchRes.body()
        return accountsResponse.accounts.toList()
    }

    fun listApps(): Set<Long> =
        globalState(null)?.keys ?: emptySet()

    fun listOptedinApps(): Set<Long> {
        val accountInfoResponse = fetchAccountInfo()
        return accountInfoResponse?.body()?.appsLocalState?.map { it.id }?.toHashSet() ?: emptySet()
    }

    private fun forApplications(
        appId: Long?,
        block: (Application) -> Pair<Long, Map<String, String>>
    ): Map<Long, Map<String, String>>? {
        val accountInfoResponse = fetchAccountInfo()

        return accountInfoResponse?.body()?.createdApps?.let { apps ->
            apps.filter { appId == null || it.id == appId }
                .associate { app -> block(app) }
        }
    }

    private fun fetchAccountInfo(): Response<com.algorand.algosdk.v2.client.model.Account>? {
        loadAddress()

        val networkClient = connectToNetwork()

        val accountInfoResponse = networkClient.AccountInformation(Address(address)).execute()
        if (!accountInfoResponse.isSuccessful) {
            return null // Not credited yet
        }

        return accountInfoResponse
    }

    fun assetBalance(assetId: Long = AlgoConfig.assetId): Double? {
        loadAddress()
        val networkClient = connectToNetwork()
        val accountInfoResponse = networkClient.AccountInformation(Address(address)).execute()
        if (!accountInfoResponse.isSuccessful) {
            throw AlgoNetworkException("Could not lookup asset balance, err ${accountInfoResponse.code()}")
        }
        val accountInfo = accountInfoResponse.body()
        val balance = accountInfo.assets.firstOrNull { it.assetId == assetId }?.amount
        return balance?.toDouble()
    }

    fun connectToNetwork(): AlgodClient {
        return AlgodClient(algoProviderConfig!!.clientApiUrl, algoProviderConfig!!.clientApiPort, algoProviderConfig!!.token, "X-API-Key")
    }

    private fun connectToIndexer(): IndexerClient {
        return IndexerClient(algoProviderConfig!!.indexerApiUrl, algoProviderConfig!!.indexerApiPort, algoProviderConfig!!.token, "X-API-Key")
    }

    fun sendAlgo(toAddress: String, amount: Double): SenderResult {
        loadAccount()
        val networkClient = connectToNetwork()

        val txn = com.algorand.algosdk.transaction.Transaction.PaymentTransactionBuilder()
            .sender(myAccount!!.address)
            .amount((1000000 * amount).toInt()) // 1 algo = 1000000 microalgos
            .receiver(Address(toAddress))
            .lookupParams(networkClient)
            .build()
        val signedTx = myAccount!!.signTransaction(txn)
        if (!sendSignedTransaction(signedTx)) {
            return SenderResult(false)
        }

        return SenderResult(true, txn.fee.toDouble() / 1000000)
    }

    fun createAsset(name: String, unitsInIssue: Long = 1000): Long? {
        loadAccount()
        val networkClient = connectToNetwork()

        val txn = com.algorand.algosdk.transaction.Transaction.AssetCreateTransactionBuilder()
            .sender(myAccount!!.address)
            .assetName("$name Asset")
            .assetUnitName(name)
            .assetDecimals(0)
            .assetTotal(unitsInIssue)
            .defaultFrozen(false)
            .lookupParams(networkClient)
            .build()
        val signedTx = myAccount!!.signTransaction(txn)

        val rawtxresponse = sendSignedTransactionForResponse(signedTx) ?: return null

        val id: String = rawtxresponse.txId
        logDebug("Transaction ID: $id")

        val confirm = waitForConfirmation(id, 10)
        return confirm.assetIndex
    }

    fun sendAsset(assetId: Long, amount: Double, toAddress: String): Boolean {
        loadAccount()
        val networkClient = connectToNetwork()

        val txn = com.algorand.algosdk.transaction.Transaction.AssetTransferTransactionBuilder()
            .sender(myAccount!!.address)
            .assetIndex(assetId)
            .assetAmount(amount.toLong())
            .assetReceiver(Address(toAddress))
            .lookupParams(networkClient)
            .build()

        val signedTx = myAccount!!.signTransaction(txn)
        return sendSignedTransaction(signedTx)
    }

    fun optInToAsset(assetId: Long = AlgoConfig.assetId): Boolean {
        loadAccount()
        val networkClient = connectToNetwork()

        val txn = com.algorand.algosdk.transaction.Transaction.AssetAcceptTransactionBuilder()
            .acceptingAccount(myAccount!!.address)
            .assetIndex(assetId)
            .lookupParams(networkClient)
            .build()
        val signedTx = myAccount!!.signTransaction(txn)
        return sendSignedTransaction(signedTx)
    }

    fun assembleFile(sourceFile: String): ByteArray {
        val source = Files.readString(Path.of(sourceFile), StandardCharsets.US_ASCII)
        return assemble(source)
    }

    fun assemble(source: String): ByteArray {
        loadAccount()
        val networkClient = connectToNetwork()
        val compileRes = TealCompile(networkClient).source(source.toByteArray()).execute()
        if (!compileRes.isSuccessful) {
            throw RuntimeException("Could not compile Teal source: ${compileRes.message()}")
        }

        return Base64.decode(compileRes.body().result)
    }

    fun deploy(approvalProgram: ByteArray, clearStateProgram: ByteArray, assetId: Long? = null): Long? {
        loadAccount()
        val networkClient = connectToNetwork()

        val txn = ApplicationCreateTransactionBuilder.Builder()
            .sender(myAccount!!.address)
            .clearStateProgram(TEALProgram(clearStateProgram))
            .approvalProgram(TEALProgram(approvalProgram))
            .localStateSchema(StateSchema(1, 1))
            .globalStateSchema(StateSchema(1, 0))
            .foreignAssets(listOf(assetId).mapNotNull { it })
            .lookupParams(networkClient)
            .build()

        val signedTx = myAccount!!.signTransaction(txn)
        val appCreateResponse = sendSignedTransactionForResponse(signedTx)
        if (appCreateResponse == null) return null

        val txId: String = appCreateResponse.txId
        logDebug("Transaction ID: $txId")

        val confirm = waitForConfirmation(txId, 10)
        logDebug("Confirmed: $confirm")
        return confirm.applicationIndex
    }

    fun closeOutApp(appId: Long): Boolean {
        loadAccount()
        val networkClient = connectToNetwork()

        val txn = ApplicationCloseTransactionBuilder.Builder()
            .sender(myAccount!!.address)
            .applicationId(appId)
            .lookupParams(networkClient)
            .build()

        val signedTx = myAccount!!.signTransaction(txn)
        val appCreateResponse = sendSignedTransactionForResponse(signedTx)
        if (appCreateResponse == null) return false

        val txId: String = appCreateResponse.txId
        logDebug("closeOutApp: Transaction ID: $txId")

        val confirm = waitForConfirmation(txId, 10)
        logDebug("closeOutApp: Confirmed: $confirm")
        return true
    }

    fun deleteApp(appId: Long): Boolean {
        loadAccount()
        val networkClient = connectToNetwork()

        val txn = ApplicationDeleteTransactionBuilder.Builder()
            .sender(myAccount!!.address)
            .applicationId(appId)
            .lookupParams(networkClient)
            .build()

        val signedTx = myAccount!!.signTransaction(txn)
        val appCreateResponse = sendSignedTransactionForResponse(signedTx)
        if (appCreateResponse == null) return false

        val txId: String = appCreateResponse.txId
        logDebug("deleteApp: Transaction ID: $txId")

        val confirm = waitForConfirmation(txId, 10)
        logDebug("deleteApp: Confirmed: $confirm")
        return true
    }

    fun callApp(appId: Long, creatorAddress: String, vararg args: Any): Boolean =
        callAppWithAsset(appId, creatorAddress, null, *args)

    fun callAppWithAsset(appId: Long, creatorAddress: String, assetId: Long?, vararg args: Any): Boolean {
        loadAccount()
        val networkClient = connectToNetwork()
        val appArgs = args.map {
            if (it is Int) {
                // Note big endian
                val argv = ByteArray(8)
                for (i in 0..7) {
                    argv[7 - i] = (it.toLong() shr (i * 8)).toByte()
                }
                argv
            } else if (it is String) {
                it.toByteArray()
            } else {
                throw RuntimeException("args must be string or Int")
            }
        }
        var builder = ApplicationCallTransactionBuilder.Builder()
            .sender(myAccount!!.address)
            .applicationId(appId)
            .args(appArgs)
            .accounts(listOf(Address(creatorAddress)))
            .lookupParams(networkClient)
        if (assetId != null) {
            builder = builder.foreignAssets(listOf(assetId))
        }
        val txn = builder.build()

        val signedTx = myAccount!!.signTransaction(txn)
        val appCallResponse = sendSignedTransactionForResponse(signedTx)
        if (appCallResponse == null) return false

        val txId: String = appCallResponse.txId
        logDebug("Application transaction ID: $txId")

        val confirm = waitForConfirmation(txId, 10)
        logDebug("Application call confirmed: $confirm")
        return true
    }

    fun optInApp(appId: Long): Boolean {
        loadAccount()
        val networkClient = connectToNetwork()

        val accountInfoResponse = fetchAccountInfo()
        if (accountInfoResponse == null) return false

        if (accountInfoResponse.body().appsLocalState.any { it.id == appId } == true) return true

        val optInTxn = ApplicationOptInTransactionBuilder.Builder()
            .sender(myAccount!!.address)
            .applicationId(appId)
            .lookupParams(networkClient)
            .build()

        val signedTx = myAccount!!.signTransaction(optInTxn)
        val appCallResponse = sendSignedTransactionForResponse(signedTx)
        if (appCallResponse == null) return false

        val txId: String = appCallResponse.txId
        logDebug("Opt in transaction ID: $txId")

        val confirm = waitForConfirmation(txId, 10)
        logDebug("Opt in confirmed: $confirm")
        return true
    }

    fun clearStateApp(appId: Long): Boolean {
        loadAccount()
        val networkClient = connectToNetwork()

        val accountInfoResponse = fetchAccountInfo()
        if (accountInfoResponse == null) return false

        if (accountInfoResponse.body().appsLocalState.none { it.id == appId }) return true

        val clearStateTxn = ApplicationClearTransactionBuilder.Builder()
            .sender(myAccount!!.address)
            .applicationId(appId)
            .lookupParams(networkClient)
            .build()

        val signedTx = myAccount!!.signTransaction(clearStateTxn)
        val appCallResponse = sendSignedTransactionForResponse(signedTx)
        if (appCallResponse == null) return false

        val txId: String = appCallResponse.txId
        logDebug("Clear state transaction ID: $txId")

        val confirm = waitForConfirmation(txId, 10)
        logDebug("Clear state confirmed: $confirm")
        return true
    }

    private fun findAllAssetTransactions(): MutableList<Transaction> {
        val indexerClient = connectToIndexer()
        val assetTransactionsResponse = SearchForTransactions(indexerClient)
            .assetId(AlgoConfig.assetId)
            .execute()
        if (!assetTransactionsResponse.isSuccessful) {
            throw AlgoNetworkException(
                "Could not lookup asset transactions, err ${assetTransactionsResponse.code()}"
            )
        }

        return assetTransactionsResponse.body().transactions
    }

    fun <T> retryingRequest(block: (that: AlgoOps) -> Response<T>): Response<T> {
        var d = 1000L

        for (n in 1..4) {
            try {
                val response = block(this)
                if (response.isSuccessful) return response
            }
            catch (ex: java.net.UnknownHostException) {
                logDebug("AlgoOps::retryingRequest threw UnknownHostException")
                ex.printStackTrace(System.err)
                throw ex
            }
            if (n < 4) Thread.sleep(d)
            d *= 2
        }

        throw AlgoNetworkException("AlgoOps::retryingRequest timed out")
    }

    fun <T,S> retrying(block: (that: S) -> T): T {
        logDebug("AlgoOps::retrying starts")
        var finalFail: Exception? = null
        var d = 1000L
        for (n in 1..4) {
            try {
                val res = block(this as S)
                logDebug("AlgoOps::retrying complete")
                return res
            } catch (ex: java.net.UnknownHostException) {
                logDebug("AlgoOps::retrying threw UnknownHostException")
                throw ex
            } catch (ex: RuntimeException) {
                if (ex.message?.endsWith("err 429") == true) {
                    logDebug("AlgoOps: $ex - retrying ($n)")
                    finalFail = ex
                } else {
                    System.err.println("AlgoOps threw uncaught RuntimeException $ex")
                    throw AlgoNetworkException("AlgoOps threw uncaught", ex)
                }
            } catch (ex: Exception) {
                logDebug("AlgoOps threw uncaught $ex")
                throw ex
            }
            if (n < 4) Thread.sleep(d)
            d *= 2
        }
        throw AlgoNetworkException("AlgoOps::retrying spun out", finalFail)
    }

    // send a transaction to the network
    fun sendSignedTransaction(signedTx: SignedTransaction): Boolean {
        val rawtxresponse = sendSignedTransactionForResponse(signedTx)
        if (rawtxresponse == null) return false

        val id: String = rawtxresponse.txId
        logDebug("Transaction ID: $id")

        val confirm = waitForConfirmation(id, 10)
        logDebug("Confirmed: $confirm")
        return true
    }

    fun sendSignedTransactionForResponse(signedTx: SignedTransaction): PostTransactionsResponse? {
        try {
            val encodedTxBytes: ByteArray = Encoder.encodeToMsgPack(signedTx)
            val headers = arrayOf("Content-Type")
            val values = arrayOf("application/x-binary")
            val rawtxresponse: Response<PostTransactionsResponse> =
                connectToNetwork().RawTransaction().rawtxn(encodedTxBytes).execute(headers, values)
            logDebug("rawtxresponse = ${rawtxresponse}")

            if (!rawtxresponse.isSuccessful()) {
                throw Exception(rawtxresponse.message())
            }

            return rawtxresponse.body()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun <T,S> waitForIndex(block: (that: S) -> T): T? {
        repeat((1..4).count()) {
            val res = block(this as S)
            if (res == null) {
                Thread.sleep(5000)
            } else {
                return res
            }
        }

        return null
    }

    /**
     * utility function to wait on a transaction to be confirmed
     * the timeout parameter indicates how many rounds do you wish to check pending transactions for
     */
    @Throws(Exception::class)
    fun waitForConfirmation(
        txID: String?,
        timeout: Int
    ): PendingTransactionResponse {
        val networkClient = connectToNetwork()

        require(txID != null && timeout > 0) { "Bad arguments for waitForConfirmation." }
        var resp: Response<NodeStatusResponse> = networkClient.GetStatus().execute()
        if (!resp.isSuccessful) {
            throw Exception(resp.message())
        }
        val nodeStatusResponse = resp.body()
        val startRound = nodeStatusResponse.lastRound + 1
        var currentRound = startRound
        while (currentRound < startRound + timeout) {
            // Check the pending transactions
            val resp2: Response<PendingTransactionResponse> =
                networkClient.PendingTransactionInformation(txID).execute()
            if (resp2.isSuccessful) {
                val pendingInfo = resp2.body()
                if (pendingInfo != null) {
                    if (pendingInfo.confirmedRound != null && pendingInfo.confirmedRound > 0) {
                        // Got the completed Transaction
                        return pendingInfo
                    }
                    if (pendingInfo.poolError != null && pendingInfo.poolError.length > 0) {
                        // If there was a pool error, then the transaction has been rejected!
                        throw Exception("The transaction has been rejected with a pool error: " + pendingInfo.poolError)
                    }
                }
            }
            resp = networkClient.WaitForBlock(currentRound).execute()
            if (!resp.isSuccessful) {
                throw Exception(resp.message())
            }
            currentRound++
        }
        throw Exception("Transaction not confirmed after $timeout rounds!")
    }

//    fun <R>blockCached(resultName: String, block: (round: Long?) -> Pair<List<R>, Long>): List<R> {
//        val cache = blockCache[resultName] as BlockCacheEntry<R>?
//        if(cache == null) {
//            val result = block(null)
//            blockCache.put(resultName, BlockCacheEntry(result.first, result.second))
//            return result.first
//        }
//
//        val blockResult = block(cache.round)
//        val result = blockResult.first.toMutableList()
//        result.addAll(cache.data)
//
//        cache.data = result
//        cache.round = blockResult.second
//        return result
//    }

}

class AlgoNetworkException(s: String, ex: Exception? = null) : Throwable(s, ex)
