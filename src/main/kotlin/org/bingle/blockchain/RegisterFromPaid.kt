package org.unknown.comms.blockchain

import com.creatotronik.util.logWarn
import org.unknown.comms.blockchain.generated.AlgoConfig

// paidAddress must have > 0.2 Algo
class RegisterFromPaid(val paidAddress: String, val passPhrase: String, val handle: String) {
    companion object {
        val price = 3.40}

    private var swapper : AlgoSwap;

    init {
        swapper = AlgoSwap(SwapConfig(AlgoConfig.appId!!, AlgoConfig.creatorAddress, AlgoConfig.assetId),
            null, passPhrase)
    }

    fun hasBalance() = (swapper.accountBalance() ?: 0.0) >= (price + 0.4)

    fun register(): Boolean {
       if (swapper.assetUsername() != null) {
            System.err.println("Asset held")
            return true
        }

        // ref initSwap.json for price (TODO: get from app price global)

        if(swapper.findIdByUsername(handle) != null) {
            logWarn("${handle} already registered, soft blocked")
            // You can (obviously) register an in-use handle, but
            // it will be useless as a search will return the first
            // instance of the handle
            return false
        }
        return swapper.swap(price, handle)
    }

//    private fun dispenser(stage: String, addressString: String, username: String): Boolean {
//        val message = mapOf(
//            "overridePassword" to "FreeLunchForMe",
//            "stage" to stage,
//            "address" to addressString,
//            "uniqueInstallId" to UUID.randomUUID().toString(),
//            "username" to username
//        )
//
//        val conn = URL("https://lmxm1g2fqb.execute-api.us-east-1.amazonaws.com/beta/bingly_dispenser")
//            .openConnection() as HttpURLConnection
//        conn.requestMethod = "POST"
//        conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
//        conn.doOutput = true
//        conn.outputStream.write(Klaxon().toJsonString(message).toByteArray())
//
//        val status = conn.responseCode
//        if((status / 100) != 2) throw RuntimeException("POST endpoint returned $status")
//        return true
//    }
}