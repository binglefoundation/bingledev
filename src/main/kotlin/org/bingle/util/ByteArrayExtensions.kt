package com.creatotronik.util

import java.lang.RuntimeException
import java.nio.ByteBuffer
import kotlin.experimental.xor

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun ByteArray.xor(other: ByteArray): ByteArray {
    if(this.size != other.size) throw RuntimeException("XOR incongruent byte arrays")

    return (this zip other).map { it.first xor it.second }.toByteArray()
}

fun Int.toByteArray(): ByteArray {
    val buffer: ByteBuffer = ByteBuffer.allocate(Integer.BYTES)
    buffer.putInt(this)
    return buffer.array()
}

fun Long.toByteArray(): ByteArray {
    val buffer: ByteBuffer = ByteBuffer.allocate(java.lang.Long.BYTES)
    buffer.putLong(this)
    return buffer.array()
}