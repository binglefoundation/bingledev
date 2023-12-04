package org.bingle.engine

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassRefTypeSignature
import org.bingle.annotations.CommandHandler
import org.bingle.command.BaseCommand
import kotlin.reflect.KClass
import kotlin.reflect.jvm.kotlinFunction

typealias CommandHandlerFunction  = (engine: IEngineState, command: BaseCommand) -> Unit
@Suppress("UNCHECKED_CAST")
class CommandRouter(val engineState: IEngineState) {

    private var commandRoutes: MutableMap<KClass<BaseCommand>, CommandHandlerFunction>

    init {
        val scanResult = ClassGraph()
            //.verbose()
            .acceptPackages("org.bingle")
            .enableAllInfo()
            .scan()

        commandRoutes = mutableMapOf()
        for(handlerClassInfo in scanResult.getClassesWithMethodAnnotation(CommandHandler::class.java.canonicalName)) {
            for(handlerFuncInfo in handlerClassInfo.methodInfo) {
                if(handlerFuncInfo.hasAnnotation(CommandHandler::class.java)) {
                    val commandType = (handlerFuncInfo.parameterInfo[1].typeDescriptor as ClassRefTypeSignature).loadClass().kotlin as? KClass<BaseCommand>
                        ?: throw RuntimeException("A @CommandHandler doesn't take a baseCommand as 2nd parameter")
                    val commandHandlerMethod = handlerFuncInfo.loadClassAndGetMethod()
                    val commandHandlerFunction = commandHandlerMethod.kotlinFunction as? CommandHandlerFunction
                        ?: throw RuntimeException("A @CommandHandler is not a CommandHandlerFunction")
                    commandRoutes[commandType] = commandHandlerFunction
                }
            }
        }
    }

    fun routeCommand(command: BaseCommand) {
        val commandHandlerFunction = commandRoutes[command::class]
            ?: throw RuntimeException("${command::class.qualifiedName} has no handler")

        commandHandlerFunction.invoke(engineState, command)
    }
}