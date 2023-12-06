package org.bingle.engine.ddb

import org.bingle.command.BaseCommand
import org.bingle.command.DdbCommand
import org.bingle.command.data.AdvertRecord

class DistributedDB(
    val myId: String,
    val relayPlan: RelayPlan
) {
    enum class State { CLIENT, LOADING, SIGNING_ON, SERVER }

    val records = mutableMapOf<String, AdvertRecord>()
    val loadingPeers = mutableSetOf<String>()
    val pendingUpdatesInLoad = mutableListOf<DdbCommand.Update>()

    var state = State.CLIENT
    var expectedRecords: Int? = null

    fun enterLoadingState(expectedRecords: Int) {
        state = State.LOADING
        this.expectedRecords = expectedRecords
        pendingUpdatesInLoad.clear()
    }


    fun validSender(senderId: String, command: DdbCommand): Boolean {
        return true // TODO: validate
    }

    private fun checkSig(id: String, payload: AdvertRecord): Boolean = true // TODO: check

    fun upsertRecord(recordId: String, payload: AdvertRecord) {
        if (!checkSig(recordId, payload)) return

        records[recordId] = payload
    }

    fun deleteRecord(recordID: String) {
        records.remove(recordID)
        // handle send in progress?
    }

}