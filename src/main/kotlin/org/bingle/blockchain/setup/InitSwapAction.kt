package org.bingle.blockchain.setup

import org.bingle.blockchain.deploy.DeploySwap

class InitSwapAction(
    val initialAssetBalance: Double,
    val initialAssetPrice: Double,
    passphrase: String,
) : SetupAction("initSwap", passphrase) {

    override fun execute(): Boolean {
        val deploySwap = DeploySwap(null, passphrase, generatedConfig!!.creatorAddress)
        if(deploySwap.setupApp(generatedConfig!!.appId!!, generatedConfig!!.assetId!!, initialAssetBalance, initialAssetPrice)) {
            println("Initialized ${generatedConfig!!.appId!!} for ${generatedConfig!!.assetId}")
            return true
        }
        return false
    }
}