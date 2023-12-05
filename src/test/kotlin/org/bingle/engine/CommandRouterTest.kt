package org.bingle.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bingle.command.BaseCommand
import org.bingle.dtls.NetworkSourceKey
import org.bingle.engine.mocks.*
import org.junit.jupiter.api.Test

class CommandRouterTest : BaseUnitTest() {
    private val mockEngine = mockk<IEngineState>()
    private val mockSender = mockk<Sender>()

    init {
        every { mockEngine.sender } returns mockSender
    }

    @Test
    fun `routes to test command handler`() {
        val commandRouter = CommandRouter(mockEngine)

        commandRouter.routeCommand(TestCommand())
        assertThat(SeenCommand.command).isInstanceOf(TestCommand::class.java)
        assertThat((SeenCommand.command as? TestCommand)?.test).isEqualTo(123)
    }

    @Test
    fun `routes to command handler and sends response return`() {
        val commandRouter = CommandRouter(mockEngine)

        every { mockEngine.sender.sendMessageToNetwork(any(), any(), any(), any()) } answers {
            val toNetworkSourceKey = it.invocation.args[0] as NetworkSourceKey
            assertThat(toNetworkSourceKey).isEqualTo(NetworkSourceKey(endpoint1))
            val messageSent = it.invocation.args[2] as TestResponse
            assertThat(messageSent.tag).isEqualTo("12345")
            assertThat(messageSent.times2).isEqualTo(1000)

            true
        }

        commandRouter.routeCommand(
            TestCommandWithResponse(500)
                .withResponseTag<TestCommandWithResponse>("12345")
                .withVerifiedId<BaseCommand>(id1)
                .withSenderAddress(
                    endpoint1
                )
        )
        assertThat(SeenCommand.command).isInstanceOf(TestCommandWithResponse::class.java)
        assertThat((SeenCommand.command as? TestCommandWithResponse)?.test).isEqualTo(500)

        verify { mockEngine.sender.sendMessageToNetwork(any(), any(), any(), any()) }
    }
}