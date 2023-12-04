package org.bingle.engine

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.bingle.engine.mocks.TestCommand
import org.bingle.engine.mocks.seenCommand
import org.junit.jupiter.api.Test

class CommandRouterTest {

    @Test
    fun `routes to test command handler`() {
        val mockEngine = mockk<Engine>()
        val commandRouter = CommandRouter(mockEngine)

        commandRouter.routeCommand(TestCommand())
        assertThat(seenCommand.command).isInstanceOf(TestCommand::class.java)
        assertThat((seenCommand.command as? TestCommand)?.test).isEqualTo(123)
    }
}