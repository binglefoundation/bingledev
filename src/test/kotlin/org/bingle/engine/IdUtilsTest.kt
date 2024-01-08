package org.bingle.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdUtilsTest {

    @Test
    fun `Extracts from CN in issuer string`() {
        val issuer = "CN=relay1.ids.bingler.net"
        val id = IdUtils.fromIssuer(issuer).id
        assertThat(id).isEqualTo("relay1");
    }
}