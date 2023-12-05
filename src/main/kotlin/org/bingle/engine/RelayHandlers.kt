package org.bingle.engine

import org.bingle.annotations.CommandHandler
import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.util.logDebug

@CommandHandler
fun relayCheckHandler(engineState: IEngineState, command: RelayCommand.Check) {
    logDebug("relayCheckHandler:: sending response message to ${command.verifiedId}")
    val senderNetworkSourceKey = NetworkSourceKey(command.senderAddress)

    engineState.sender.sendMessageToNetwork(
        senderNetworkSourceKey,
        command.verifiedId,
        RelayCommand.CheckResponse(1).withTag(command.responseTag),
        null
    )
}

@CommandHandler
fun relayListenHandler(engineState: IEngineState, command: RelayCommand.Listen) {
    logDebug("relayListenHandler:: ${engineState.currentEndpoint.port} got listen with ${command.senderAddress} ${command.verifiedId}")
    val senderNetworkSourceKey = NetworkSourceKey(command.senderAddress)

    engineState.turnRelayProtocol.relayListen(command.senderAddress, command.verifiedId)
    engineState.sender.sendMessageToNetwork(
        senderNetworkSourceKey,
        command.verifiedId,
        RelayCommand.ListenResponse().withTag(command.responseTag),
        null
    )
}

@CommandHandler
fun relayCallHandler(engineState: IEngineState, command: RelayCommand.Call) {
    logDebug("relayCallHandler:: got call to ${command.calledId}")

    val channel =
        engineState.turnRelayProtocol.relayCall(command.calledId, command.senderAddress)
    if(channel==null) {
        logDebug("RelayApp:: could not allocate channel for ${command.calledId}")
    }
    logDebug("relayCallHandler on ${engineState.currentEndpoint} sender = ${command.senderAddress} relayedChannels=${engineState.turnRelayProtocol.relayedChannels}")
    val senderNetworkSourceKey = NetworkSourceKey(command.senderAddress)

    engineState.sender.sendMessageToNetwork(
        senderNetworkSourceKey,
        command.verifiedId,
        RelayCommand.CallResponse(command.calledId, channel!!).withTag(command.responseTag),
        null
    )
}

@CommandHandler
fun relayCallResponseHandler(_engineState: IEngineState, _command: RelayCommand.CallResponse) {}

@CommandHandler
fun relayKeepAliveHandler(engineState: IEngineState, command: RelayCommand.KeepAlive) {
    engineState.turnRelayProtocol.keepAlive(command.verifiedId)
}
