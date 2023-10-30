package org.bingle.command

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PingTest {

    @Test
    fun `can decode and encode from and to map`() {
        val pingCommand = Ping.Ping( "1234")
        val mapPingCommand = pingCommand.toMap()
        assertThat(mapPingCommand).isEqualTo(mapOf("type" to "ping.ping", "senderId" to "1234"))

        val encodedPingCommand = BaseCommand.fromMap(mapPingCommand)
        assertThat(encodedPingCommand).isEqualTo(pingCommand)
    }

    @Test
    fun `can decode and encode response from and to map`() {
        val responseCommand = Ping.Response( "1234")
        val mapResponseCommand = responseCommand.toMap()
        assertThat(mapResponseCommand).isEqualTo(mapOf("type" to "ping.response", "verifiedId" to "1234"))

        val encodedResponseCommand = BaseCommand.fromMap(mapResponseCommand)
        assertThat(encodedResponseCommand).isEqualTo(responseCommand)
    }

    @Test
    fun `can decode and encode from and to json`() {
        val pingCommand = Ping.Ping( "1234")
        val pingCommandJson = pingCommand.toJson()
        assertThat(pingCommandJson).isEqualTo("{\"senderId\" : \"1234\", \"type\" : \"ping.ping\"}")

        val encodedPingCommand = BaseCommand.fromJson(pingCommandJson)
        assertThat(encodedPingCommand).isEqualTo(pingCommand)
    }
}