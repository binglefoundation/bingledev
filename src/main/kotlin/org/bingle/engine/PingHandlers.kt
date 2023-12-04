package org.bingle.engine

import org.bingle.annotations.CommandHandler
import org.bingle.command.Ping

@CommandHandler
fun pingHandler(engineState: IEngineState, command: Ping.Ping) {
    command.verifiedId?.let {
        engineState.sender.sendMessageToId(it, Ping.Response(), null)
    }
    ?:  throw RuntimeException("Ping message received with no verifiedId")
}

@CommandHandler
fun pingResponseHandler(engineState: IEngineState, command: Ping.Response) {
    engineState.pinger.onResponse(command)
}