package org.bingle.engine.ddb

import org.bingle.annotations.CommandHandler
import org.bingle.command.BaseCommand
import org.bingle.command.DdbCommand
import org.bingle.engine.IEngineState

@CommandHandler
fun ddbCommandUpdate(engineState: IEngineState, updateCommand: DdbCommand.Update): BaseCommand {
    // This handles derived commands UpsertResolve and DeleteResolve
    val checkRes = checkCommand(engineState, updateCommand)
    if(null != checkRes) return BaseCommand(fail = checkRes)

    if (engineState.distributedDB.state == DistributedDB.State.LOADING) {
        engineState.distributedDB.pendingUpdatesInLoad.add(updateCommand)
        return DdbCommand.PendingResponse()
    } else {
        when (updateCommand) {
            is DdbCommand.UpsertResolve -> engineState.distributedDB.upsertRecord(
                updateCommand.updateId,
                updateCommand.record
            )

            is DdbCommand.DeleteResolve -> engineState.distributedDB.deleteRecord(updateCommand.updateId)
        }
    }

    return DdbCommand.UpdateResponse()
}

@CommandHandler
fun ddbQueryResolve(engineState: IEngineState, command: DdbCommand.QueryResolve): BaseCommand {
    val checkRes = checkCommand(engineState, command)
    if(null != checkRes) return BaseCommand(fail = checkRes)

    val res = engineState.distributedDB.records[command.id] ?: return DdbCommand.QueryResponse(found = false)

    return DdbCommand.QueryResponse(true, res)
}

@CommandHandler
fun ddbSignon(engineState: IEngineState, command: DdbCommand.Signon) {
    val checkRes = checkCommand(engineState, command)
    if(null != checkRes) return

    checkValidRelay(command.verifiedId)
    engineState.distributedDB.loadingPeers.remove(command.verifiedId)

    // TODO: myId in enginestate top level
    val broadcastNext = engineState.distributedDB.relayPlan.next(-1, engineState.distributedDB.myId, command.startId)

    broadcastNext.forEach {
        engineState.sender.sendMessageToId(it, command, null)
    }

    engineState.distributedDB.relayPlan.addEpoch(command.verifiedId)

    // broadcast the epoch change to client nodes
}

@CommandHandler
fun ddbGetEpoch(engineState: IEngineState, command: DdbCommand.GetEpoch): BaseCommand {
    val checkRes = checkCommand(engineState, command)
    if(null != checkRes) return BaseCommand(fail = checkRes)

    val epochParams = engineState.distributedDB.relayPlan.getEpochParams(command.epochId)
    return DdbCommand.GetEpochResponse(
            epochParams.epochId,
            epochParams.treeOrder,
            epochParams.relayIds
        )
}

@CommandHandler
fun ddbInitResolve(engineState: IEngineState, command: DdbCommand.InitResolve) {
    val checkRes = checkCommand(engineState, command)
    if(null != checkRes) {
        engineState.sender.sendMessageToId(command.verifiedId, BaseCommand(fail = checkRes), null)
        return
    }

    engineState.distributedDB.loadingPeers.add(command.verifiedId)

    val numRecords = engineState.distributedDB.records.size
    engineState.sender.sendMessageToId(command.verifiedId,
        DdbCommand.InitResponse(numRecords).withResponseTag(command.responseTag), null)

    // handle deleted records
    engineState.distributedDB.records.entries.forEachIndexed { index, record ->
        if (index < numRecords) {
            engineState.sender.sendMessageToId(command.verifiedId, DdbCommand.DumpResolveResponse(
                index,
                record.key,
                record.value
            ).withResponseTag(command.responseTag), null)
        }
    }
}
@CommandHandler
fun ddbDumpResolve(engineState: IEngineState, command: DdbCommand.DumpResolve) {
    // We are a server that is initializing and received a dump of resolve messages
    val checkRes = checkCommand(engineState, command)
    if(null != checkRes) return

    // TODO: must have id
    engineState.distributedDB.records[command.record.id!!] = command.record
    if (engineState.distributedDB.records.size == engineState.distributedDB.expectedRecords) {
        engineState.distributedDB.pendingUpdatesInLoad.forEach {
            ddbCommandUpdate(engineState, it)
        }
        engineState.distributedDB.pendingUpdatesInLoad.clear()

        engineState.distributedDB.state = DistributedDB.State.SIGNING_ON
        // Tell the server we have all data and can be attached to network
        engineState.sender.sendMessageToId(command.verifiedId, DdbCommand.Signon(engineState.distributedDB.myId), null)   // We are originating

        engineState.distributedDB.state = DistributedDB.State.SERVER // Do we wait for a response
    }
}


private fun checkCommand(engineState: IEngineState, command: DdbCommand): String? {
    val senderId = command.verifiedId
    if (!engineState.distributedDB.validSender(senderId, command)) return "invalid_sender"
    checkBroadcast(engineState, command)

    return null
}

private fun checkBroadcast(engineState: IEngineState, command: DdbCommand) {
    if (command !is DdbCommand.Update) return

    // we start at the relays id, not the senders
    val broadcastNext = engineState.distributedDB.relayPlan.next(command.epoch, engineState.distributedDB.myId, engineState.distributedDB.myId)

    broadcastNext.forEach {
        engineState.sender.sendMessageToId(it, command, null)
    }
}

private fun checkValidRelay(senderId: String): Boolean {
    return true // TODO: check blockchain
}
