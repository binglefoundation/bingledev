package org.bingle.command

import com.beust.klaxon.TypeFor

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
data class TextMessageCommand(val text: String) : BaseCommand() {
    var senderHandle: String? = null
}

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
data class ResponseCommand(val response: String) : BaseCommand()
