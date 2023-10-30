package org.unknown.comms.blockchain.setup

import org.unknown.comms.blockchain.AlgoOps

class AssetCreateAction(
    val assetName: String,
    val unitsInIssue: Long,
    passphrase: String,
) : SetupAction("assetCreate", passphrase) {

    override fun execute(): Boolean {
        val algoOps = AlgoOps(null, passphrase, generatedConfig?.creatorAddress)
        val assetId = algoOps.createAsset(assetName, unitsInIssue)
        if(assetId != null) {
            println("Created ${assetName} as ${assetId}")

            updateGenerated(generatedConfig!!.creatorAddress, assetId, null)
            return true
        }
        return false
    }

}