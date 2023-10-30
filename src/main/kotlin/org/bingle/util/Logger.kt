package org.bingle.util

import java.text.SimpleDateFormat
import java.util.*

var formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

fun logDebug(message: String) {
    val timeStamp = formatter.format(Date())
    System.out.println("${timeStamp} ${message}")
}

fun logError(message: String) {
    val timeStamp = formatter.format(Date())
    System.err.println("${timeStamp} ${message}")
}

fun logWarn(message: String) = logError(message)
