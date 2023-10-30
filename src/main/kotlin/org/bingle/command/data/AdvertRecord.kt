package org.bingle.command.data

import com.beust.klaxon.Json
import com.beust.klaxon.JsonObject
import org.bingle.util.logError
import java.net.InetSocketAddress
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.*

data class AdvertRecord(
    @Json(serializeNull = false)
    val id: String? = null,
    @Json(serializeNull = false)
    var endpoint: InetSocketAddress? = null,
    val amRelay: Boolean? = false,
    @Json(serializeNull = false)
    val relayId: String? = null,
    @Json(serializeNull = false)
    val relaySig: String? = null,
    val date: Date = Date(),
    @Json(serializeNull = false)
    val sig: String? = null,
) {

    constructor(txt: String) : this(
        parseField(txt, "i"),
        parseEndpoint(txt),
        parseAmRelay(txt),
        parseField(txt, "x"),
        parseField(txt, "t"),
        parseDate(txt) ?: Date(),
        parseField(txt, "s"),
    )

    constructor(ob: JsonObject) : this(
        ob["id"].toString(),
        InetSocketAddress(ob["endpoint"].toString().substring(1).split(':')[0],
            ob["endpoint"].toString().split(':')[1].toInt()),
        ob["amRelay"].toString() == "true",
        ob["relayId"].toString(),
        ob["relaySig"].toString(),
        Date.from( OffsetDateTime.parse(ob["date"].toString()).toInstant()),
        ob["sig"].toString(),
    )

    override fun toString() = listOf(
        "i=${id}",
        endpoint?.let { "h=${it.address.hostAddress}" },
        endpoint?.let { "p=${it.port}" },
        "r=${if (amRelay==true) 2 else 0}",
        "d=${isoDateFormat.format(date)}",
        relayId?.let { "x=${it}" },
        relaySig?.let { "t=${it}" },
    ).mapNotNull { it }.joinToString(" ")

    fun isComplete(): Boolean =
        id != null
                && (endpoint != null || (relayId != null && relaySig != null))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdvertRecord) return false

        if (id != other.id) return false
        if (endpoint != other.endpoint) return false
        if (amRelay != other.amRelay) return false
        if (relayId != other.relayId) return false
        if (relaySig != other.relaySig) return false
        if (sig != other.sig) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (endpoint?.hashCode() ?: 0)
        result = 31 * result + (amRelay?.hashCode() ?: 0)
        result = 31 * result + (relayId?.hashCode() ?: 0)
        result = 31 * result + (relaySig?.hashCode() ?: 0)
        result = 31 * result + (sig?.hashCode() ?: 0)
        return result
    }

    companion object {
        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")

        private fun parseField(txt: String, fieldCode: String): String? =
            Regex("${fieldCode}=(.+?)(?:\\s|\$)").find(txt)?.groupValues?.get(1)

        private fun parseEndpoint(txt: String): InetSocketAddress? {
            val host = parseField(txt, "h")
            val port = parseField(txt, "p")
            if (host != null && port != null) return InetSocketAddress(host, port.toInt())
            return null
        }

        private fun parseAmRelay(txt: String): Boolean = (parseField(txt, "r")?.toIntOrNull() ?: 0) > 0
        private fun parseDate(txt: String): Date? {
            return try {
                val dateText = parseField(txt, "d")
                dateText?.let { isoDateFormat.parse(it) }
            } catch (ex: ParseException) {
                logError("AdvertRecord::parseDate ${txt} threw ${ex}")
                null
            } catch (ex: NumberFormatException) {
                logError("AdvertRecord::parseDate ${txt} threw ${ex}")
                null
            }
        }
    }
}
