package org.bingle.engine.mocks

import com.beust.klaxon.TypeFor
import org.bingle.annotations.CommandHandler
import org.bingle.command.BaseCommand
import org.bingle.engine.IEngineState
import org.bingle.util.logDebug

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
open class TestCommand(val test:Int=123): BaseCommand()

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
class TestCommandWithResponse(test:Int=123): TestCommand(test)

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
data class TestResponse(val times2: Int): BaseCommand()

object SeenCommand {
    var command: BaseCommand? = null
}
@CommandHandler
fun testCommandHandler(engine: IEngineState, command: TestCommand) {
    SeenCommand.command = command
    logDebug("testCommandHandler got called with ${command}")
}

@CommandHandler
fun testCommandWithResponseHandler(engine: IEngineState, command: TestCommandWithResponse): TestResponse {
    SeenCommand.command = command
    logDebug("testCommandWithResponseHandler got called with ${command}")
    return TestResponse(command.test * 2)
}