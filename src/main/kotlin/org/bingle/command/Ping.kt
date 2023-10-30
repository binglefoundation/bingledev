package org.bingle.command

import com.beust.klaxon.TypeFor

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
open class Ping() : BaseCommand() {

    data class Ping(val senderId: String) : org.bingle.command.Ping()
    data class Response(val verifiedId: String) : org.bingle.command.Ping()

}