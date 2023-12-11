package org.bingle.util

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest

class SHA256Hash {
    val messageDigest = MessageDigest.getInstance("SHA-256")

    /**
     * get a hash as an unsigned long, note this is only 64 bits
     */
    fun hashULong(text: String): Long {
        messageDigest.reset()
        val bytes = messageDigest.digest(text.encodeToByteArray())
        return ByteBuffer.wrap(bytes).getLong()
    }
    /**
     * get a hash as a positive big integer
     */
    fun hashBigInteger(text: String): BigInteger {
        messageDigest.reset()
        val bytes = messageDigest.digest(text.encodeToByteArray())
        val unsignedBytes = ByteArray(bytes.size + 1)
        System.arraycopy(bytes, 0, unsignedBytes, 1, bytes.size)
        unsignedBytes[0] = 0.toByte()
        return BigInteger(unsignedBytes)
    }

}