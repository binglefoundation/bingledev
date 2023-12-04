package org.bingle.engine.mocks

import com.beust.klaxon.TypeFor
import org.bingle.annotations.CommandHandler
import org.bingle.command.BaseCommand
import org.bingle.engine.Engine
import org.bingle.util.logDebug

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
data class TestCommand(val test:Int=123): BaseCommand()

object seenCommand {
    var command: BaseCommand? = null
}
@CommandHandler
fun testCommandHandler(engine: Engine, command: TestCommand) {
    seenCommand.command = command
    logDebug("testCommandHandler got called with ${command}")
}
