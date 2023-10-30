package org.bingle.command

import org.assertj.core.api.Assertions
import org.bingle.command.data.AdvertRecord
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DdbTest {
    @Test
    fun `can decode and encode UpsertResolve from and to map`() {
        val endpoint1234 = InetSocketAddress("1.2.3.4", 5005)
        val upsertResolve = Ddb.UpsertResolve( AdvertRecord("1234", endpoint1234),
            "2000", 2)
        val mapUpsertResolve = upsertResolve.toMap()
        Assertions.assertThat(mapUpsertResolve["startId"]).isEqualTo("2000")
        Assertions.assertThat(mapUpsertResolve["epoch"]).isEqualTo(2)
        Assertions.assertThat((mapUpsertResolve["record"] as? Map<String, Any>)?.get("id")).isEqualTo("1234")
        Assertions.assertThat((mapUpsertResolve["record"] as? Map<String, Any>)?.get("endpoint")).isEqualTo(endpoint1234.toString())

        val encodedUpsertResolve = BaseCommand.fromMap(mapUpsertResolve)
        Assertions.assertThat(encodedUpsertResolve).isEqualTo(upsertResolve)
    }
}