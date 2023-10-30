package org.unknown.comms.apps.ddb

import org.unknown.comms.Advertiser
import org.unknown.comms.command.DdbCommand

class DistributedDB(
    private val myId: String,
    private val relayPlan: RelayPlan,
    private val send: (id: String, command: ISendableMessage) -> Boolean
) {
    enum class State { CLIENT, LOADING, SIGNING_ON, SERVER }

    private val records = mutableMapOf<String, Advertiser.AdvertRecord>()
    private val loadingPeers = mutableSetOf<String>()
    private val pendingUpdatesInLoad = mutableListOf<DdbCommand.Update>()

    var state = State.CLIENT
    var expectedRecords: Int? = null

    fun execute(senderId: String, command: DdbCommand) {
        if (!validSender(senderId, command)) return
        checkBroadcast(command)

        (command as? DdbCommand.Update)?.let {
            if (state == State.LOADING) {
                pendingUpdatesInLoad.add(command)
                return
            } else {
                handleUpdate(command)
            }

            send(
                senderId, DdbCommand.UpdateResponse(command.responseTag)
            )
            return
        }

        when (command) {
            is DdbCommand.QueryResolve -> queryResolve(senderId, command.id, command.responseTag)
            is DdbCommand.Signon -> signon(senderId, command)
            is DdbCommand.GetEpoch -> getEpoch(senderId, command.epochId, command.responseTag)
            is DdbCommand.InitResolve -> initResolve(senderId, command.responseTag)
            is DdbCommand.DumpResolve -> dumpResolve(senderId, command.responseTag, command.record)
            else -> println("Unexpected command ${command.type}")
        }
    }


    fun enterLoadingState(expectedRecords: Int) {
        state = State.LOADING
        this.expectedRecords = expectedRecords
        pendingUpdatesInLoad.clear()
    }

    private fun handleUpdate(command: DdbCommand.Update) {
        when (command) {
            is DdbCommand.UpsertResolve -> upsertRecord(
                command.startId,
                command.record
            )

            is DdbCommand.DeleteResolve -> deleteRecord(command.startId)
        }
    }

    private fun getEpoch(querySenderId: String, epoch: Int, responseTag: String?) {
        val epochParams = relayPlan.getEpochParams(epoch)
        send(
            querySenderId, DdbCommand.GetEpochResponse(
                responseTag,
                querySenderId,
                epochParams.epochId,
                epochParams.treeOrder,
                epochParams.relayIds
            )
        )
    }

    private fun signon(senderId: String, command: DdbCommand.Signon) {
        checkValidRelay(senderId)
        loadingPeers.remove(senderId)

        val broadcastNext = relayPlan.next(-1, myId, command.startId)

        broadcastNext.forEach {
            send(it, command)
        }

        relayPlan.addEpoch(senderId)

        // broadcast the epoch change to client nodes
    }

    private fun checkValidRelay(senderId: String): Boolean {
        return true // TODO: check blockchain
    }

    private fun checkBroadcast(command: DdbCommand) {
        if (command !is DdbCommand.Update) return

        // we start at the relays id, not the senders
        val broadcastNext = relayPlan.next(command.epoch, myId, myId)

        broadcastNext.forEach {
            send(it, command)
        }
    }

    private fun validSender(senderId: String, command: DdbCommand): Boolean {
        return true // TODO: validate
    }

    private fun checkSig(id: String, payload: Advertiser.AdvertRecord): Boolean = true // TODO: check

    private fun upsertRecord(recordId: String, payload: Advertiser.AdvertRecord) {
        if (!checkSig(recordId, payload)) return

        records[recordId] = payload
    }

    private fun deleteRecord(recordID: String) {
        records.remove(recordID)
        // handle send in progress?
    }

    private fun queryResolve(querySenderId: String, recordID: String, responseTag: String?) {
        val res = records[recordID]
        if (res == null) {
            send(
                querySenderId, DdbCommand.QueryResponse(responseTag, querySenderId, false)
            )
            return
        }
        send(
            querySenderId, DdbCommand.QueryResponse(responseTag, querySenderId, true, res)
        )
    }

    private fun initResolve(senderId: String, responseTag: String?) {
        loadingPeers.add(senderId)

        val numRecords = records.size
        send(senderId, DdbCommand.InitResponse(responseTag, senderId, numRecords))

        // handle deleted records
        records.entries.forEachIndexed { index, record ->
            if (index < numRecords) {
                send(senderId, DdbCommand.DumpResolveResponse(
                    responseTag,
                    senderId,
                    index,
                    record.key,
                    record.value
                ))
            }
        }
    }

    private fun dumpResolve(senderId: String, responseTag: String?, record: Advertiser.AdvertRecord) {
        records[record.id!!] = record
        if (records.size == expectedRecords) {
            pendingUpdatesInLoad.forEach {
                handleUpdate(it)
            }
            pendingUpdatesInLoad.clear()

            state = State.SIGNING_ON
            send(senderId, DdbCommand.Signon(senderId, myId))   // We are originating

            state = State.SERVER // Do we wait for a response
        }
    }
}