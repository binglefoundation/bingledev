package org.bingle.engine

import org.bingle.annotations.CommandHandler
import org.bingle.command.RelayCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.util.logDebug
import org.bingle.util.logWarn

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

@CommandHandler
fun triangleTest1Handler(engineState: IEngineState, command: RelayCommand.TriangleTest1) {
    logDebug("triangleTest1Handler message, find relay to pass on message")
    val relay = engineState.relayFinder.find()
    if(relay == null) {
        logWarn("triangleTest1Handler message: No relay found to pass on triangleTest1")
    }
    else {
        engineState.sender.sendMessageToId(
            relay.first,
            RelayCommand.TriangleTest2(command.verifiedId, command.checkingEndpoint,
            ).withTag(command.responseTag),
            null
        )
    }
}

@CommandHandler
fun triangleTest2Handler(engineState: IEngineState, command: RelayCommand.TriangleTest2) {
    // This will be picked up by tag handler in the original sender
    // and return the TriangleTest3 response
    val networkSourceKey = NetworkSourceKey(command.checkingEndpoint)

    engineState.sender.sendMessageToNetwork(networkSourceKey, command.checkingId,
        RelayCommand.TriangleTest3().withTag(command.responseTag),
        null
    )
}