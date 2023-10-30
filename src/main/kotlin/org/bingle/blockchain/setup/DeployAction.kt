package org.unknown.comms.blockchain.setup

import org.unknown.comms.blockchain.AlgoOps

class DeployAction(
    val appName: String,
    passphrase: String
) : SetupAction("deploy", passphrase) {

    override fun execute(): Boolean {
        val algoOps = AlgoOps(null, passphrase, generatedConfig?.creatorAddress)
        val clearStateProgram = algoOps.assembleFile("pyteal/${appName}_clear_state.teal")
        val approvalProgram = algoOps.assembleFile("pyteal/${appName}_approval.teal")
        val createdAppId = algoOps.deploy(approvalProgram, clearStateProgram, generatedConfig!!.assetId)
        if(createdAppId != null) {
            println("Deployed ${appName} as ${createdAppId}")
            updateGenerated(generatedConfig!!.creatorAddress, generatedConfig!!.assetId!!, createdAppId)
            return true
        }
        return false
    }

}