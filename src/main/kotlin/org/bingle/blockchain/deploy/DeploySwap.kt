package org.unknown.comms.blockchain.deploy

import org.unknown.comms.blockchain.AlgoOps
import org.unknown.comms.interfaces.IKeyProvider

class DeploySwap(keyProvider: IKeyProvider?=null, passphrase: String?=null, address: String?=null)
    : AlgoOps(keyProvider, passphrase, address){

    fun deploy(appName: String, assetId: Long, initialAssetBalance: Double, initialAssetPrice: Double, initialAlgoAmount: Double = 0.21): Long? {
        val createdAppId = deployAppWithAsset(appName, assetId)
        if(createdAppId == null) {
            println("Could not deploy ${appName}")
            return null
        }

        if(!setupApp(createdAppId, assetId, initialAssetBalance, initialAssetPrice, initialAlgoAmount)) {
            return null
        }

        return createdAppId
    }

    fun setupApp(appId: Long, assetId: Long, initialAssetBalance: Double, initialAssetPrice: Double, initialAlgoAmount: Double = 0.21):Boolean {
        println("send initial algo ${initialAlgoAmount}")
        sendAlgo(contractAddress(appId), initialAlgoAmount)

        println("InitAsset")
        val initAssetRes = callAppWithAsset(appId, address!!, assetId, "InitAsset")
        if(!initAssetRes) {
            println("Could not InitAsset")
            return false
        }

        println("sendAsset from ${address} ")
        val sendAssetRes = sendAsset(assetId, initialAssetBalance, contractAddress(appId))
        if(!sendAssetRes) {
            println("Could not send sendAsset")
            return false
        }

        println("setPrice to $initialAssetPrice")
        val setPriceRes = callApp(appId, address!!, "SetPrice", (1e6 * initialAssetPrice).toInt())
        if(!setPriceRes) {
            println("Could not set price")
            return false
        }

        return true
    }

    private fun deployAppWithAsset(appName: String, assetId: Long): Long? {
        val clearStateProgram = assembleFile("pyteal/${appName}_clear_state.teal")
        val approvalProgram = assembleFile("pyteal/${appName}_approval.teal")
        return deploy(approvalProgram, clearStateProgram, assetId)
    }
}