package org.bingle.engine

import io.mockk.*
import org.bingle.command.BaseCommand
import org.bingle.dtls.DTLSParameters
import org.bingle.dtls.IDTLSConnect
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.mocks.*
import org.bingle.engine.mocks.endpoint1
import org.bingle.engine.mocks.endpoint2
import org.bingle.engine.mocks.endpointRelay
import org.bingle.interfaces.ICommsConfig
import org.bingle.util.logDebug
import java.nio.charset.Charset
import kotlin.reflect.KClass

open class BaseUnitTest {
    init {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    val id1nsk = NetworkSourceKey(endpoint1)
    val id2nsk = NetworkSourceKey(endpoint2)
    val idRelayNsk = NetworkSourceKey(endpointRelay)

    val mockDtlsConnect = mockk<IDTLSConnect>()
    open var mockCommsConfig: ICommsConfig = MockCommsConfig(mockDtlsConnect)
    lateinit var dtlsParameters: DTLSParameters

    protected fun <R : BaseCommand> mockSending(
        toId: String,
        toNsk: NetworkSourceKey,
        matchCommandClass: KClass<*>,
        makeResponse: (args: List<Any?>) -> R?
    ){
        every {
            mockDtlsConnect.send(
                toNsk, match { sendingBytes ->
                    val command = BaseCommand.fromJson(sendingBytes)
                    logDebug("mockSending tries match ${command.javaClass.canonicalName} == ${matchCommandClass.java.canonicalName}")
                    command.javaClass == matchCommandClass.java
                },
                any()
            )
        } answers answer@{
            val sendingCommand = BaseCommand.fromJson(it.invocation.args[1] as ByteArray)

            var message = makeResponse.invoke(it.invocation.args)?.withVerifiedId<R>(toId)
            if(null==message) return@answer true

            if (sendingCommand.hasResponseTag()) {
                message = message.withTag(sendingCommand.responseTag)
            }

            val messageBytes = message!!.toJson().toByteArray(Charset.defaultCharset())
            dtlsParameters.onMessage(id1nsk, id1, messageBytes, messageBytes.size)
            true
        }
    }

    protected fun <R : BaseCommand> verifySending(
        toNsk: NetworkSourceKey,
        matchCommandClass: KClass<*>
    ) {
        verify {
            mockDtlsConnect.send(
                toNsk, match { sendingBytes ->
                    val command = BaseCommand.fromJson(sendingBytes)
                    command.javaClass == matchCommandClass.java
                },
                any()
            )
        }
    }
}