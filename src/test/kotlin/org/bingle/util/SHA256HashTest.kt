package org.bingle.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger

class SHA256HashTest {
    @Test
    fun `Produces a range of positive BigIntegers less than 2^256` () {
        val hasher = SHA256Hash()
        val numSlots = 20
        val hashes = (1..numSlots).map {
            val id = "Test${it}"

            val hash = hasher.hashBigInteger(id)

            assertThat(hash.compareTo(BigInteger.ZERO)).isNotNegative()

            hash
        }

        val partSize = BigInteger.ONE.shiftLeft(256).divide(BigInteger.valueOf(numSlots.toLong()))
        val slots = hashes.map {
            it.divide(partSize).intValueExact()
        }
        assertThat(slots.sum() / numSlots).isBetween(9,11)
    }
}