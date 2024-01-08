package org.bingle.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


object DateUtil {
    fun isoDate(date: Date = Date()): String {
        val tz = TimeZone.getTimeZone("UTC")
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'") // Quoted "Z" to indicate UTC, no timezone offset

        df.timeZone = tz
        return df.format(date)
    }
}