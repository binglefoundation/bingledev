package org.bingle.command

import com.beust.klaxon.TypeFor

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
open class Ping() : BaseCommand() {

    class Ping() : org.bingle.command.Ping()
    class Response() : org.bingle.command.Ping()

}