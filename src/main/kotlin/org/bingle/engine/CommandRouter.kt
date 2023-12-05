package org.bingle.engine

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassRefTypeSignature
import org.bingle.annotations.CommandHandler
import org.bingle.command.BaseCommand
import org.bingle.dtls.NetworkSourceKey
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.kotlinFunction

typealias CommandHandlerFunction = (engine: IEngineState, command: BaseCommand) -> Unit
typealias CommandHandlerReturningFunction = (engine: IEngineState, command: BaseCommand) -> BaseCommand

@Suppress("UNCHECKED_CAST")
class CommandRouter(private val engineState: IEngineState) {

    private class CommandHandlerEntry(
        val returnsResponse: Boolean,
        val handler: CommandHandlerFunction? = null,
        val handlerWithReturn: CommandHandlerReturningFunction? = null
    )

    private var commandRoutes: MutableMap<KClass<BaseCommand>, CommandHandlerEntry>

    init {
        val scanResult = ClassGraph()
            //.verbose()
            .acceptPackages("org.bingle")
            .enableAllInfo()
            .scan()

        commandRoutes = mutableMapOf()
        for (handlerClassInfo in scanResult.getClassesWithMethodAnnotation(CommandHandler::class.java.canonicalName)) {
            for (handlerFuncInfo in handlerClassInfo.methodInfo) {
                if (handlerFuncInfo.hasAnnotation(CommandHandler::class.java)) {
                    val commandType =
                        (handlerFuncInfo.parameterInfo[1].typeDescriptor as ClassRefTypeSignature).loadClass().kotlin as? KClass<BaseCommand>
                            ?: throw RuntimeException("A @CommandHandler doesn't take a baseCommand as 2nd parameter")

                    val resultType =
                        (handlerFuncInfo.typeDescriptor.resultType as? ClassRefTypeSignature)?.classInfo?.loadClass()?.kotlin
                    val returnsResponse = resultType?.isSubclassOf(BaseCommand::class) ?: false

                    val commandHandlerMethod = handlerFuncInfo.loadClassAndGetMethod()

                    if (returnsResponse) {
                        val commandHandlerReturningFunction =
                            commandHandlerMethod.kotlinFunction as? CommandHandlerReturningFunction
                                ?: throw RuntimeException("A @CommandHandler is not a CommandHandlerReturningFunction")
                        commandRoutes[commandType] =
                            CommandHandlerEntry(true, handlerWithReturn = commandHandlerReturningFunction)
                    } else {
                        val commandHandlerFunction = commandHandlerMethod.kotlinFunction as? CommandHandlerFunction
                            ?: throw RuntimeException("A @CommandHandler is not a CommandHandlerFunction")
                        commandRoutes[commandType] = CommandHandlerEntry(false, commandHandlerFunction)
                    }
                }
            }
        }
    }

    fun routeCommand(command: BaseCommand) {
        val commandHandlerEntry = commandRoutes[command::class]
            ?: throw RuntimeException("${command::class.qualifiedName} has no handler")

        if (commandHandlerEntry.returnsResponse) {
            val responseCommand = commandHandlerEntry.handlerWithReturn?.invoke(engineState, command)
            val senderNetworkSourceKey = NetworkSourceKey(command.senderAddress)

            engineState.sender.sendMessageToNetwork(
                senderNetworkSourceKey,
                command.verifiedId,
                (responseCommand ?: BaseCommand("null_response")).withTag(command.responseTag),
                null
            )
        } else {
            commandHandlerEntry.handler?.invoke(engineState, command)
        }
    }
}