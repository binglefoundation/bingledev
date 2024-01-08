package org.bingle.simulated

import org.bingle.simulated.simulator.Simulator
import org.junit.jupiter.api.Test

class Scenarios {
    @Test
    fun `2 nodes, root relays, single message`() {
        val simulator = Simulator()
            .node("relay1", "mockuserRelay1")
            .relay(Simulator.RelayType.ROOT_RELAY)
            .immediately().init()
            .after(20).send()
            .node("relay2", "mockUserRelay2")
            .relay(Simulator.RelayType.ROOT_RELAY)
            .immediately().init()

        simulator.runUntilFinished()
    }
}