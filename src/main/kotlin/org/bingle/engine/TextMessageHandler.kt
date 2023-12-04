package org.bingle.engine

import org.bingle.annotations.CommandHandler
import org.bingle.command.TextMessageCommand

@CommandHandler fun textMessageHandler(engineState: IEngineState, command: TextMessageCommand) {
    command.verifiedId?.let {
        command.senderHandle =
            engineState.chainAccess.findUsernameByAddress(it)
    }

    engineState.config.onMessage(command)
}