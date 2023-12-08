package org.bingle.command

import com.beust.klaxon.*
import com.google.common.base.CaseFormat
import org.apache.commons.text.CaseUtils
import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KClass

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
open class BaseCommand(@Json(serializeNull = false) val fail: String? = null) : ISendableCommand {

    val type = this.javaClass.name.replace("org.bingle.command.", "").split("$")
        .map { CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, it) }.joinToString(".")

    // These properties are lateinit to the code
    // (and immutable) but can be serialized in klaxon
    // and have with ops to do fluent setting after a constructor
    @Json(serializeNull = false, name="senderAddress")
    var jsSenderAddress: InetSocketAddress?
        get() = if(::senderAddress.isInitialized) senderAddress else null
        set(v) {
            if(null != v) {
                senderAddress = v
            }
        }
    fun hasSenderAddress() = ::senderAddress.isInitialized
    @Json(ignored = true)
    lateinit var senderAddress: InetSocketAddress

    @Json(serializeNull = false, name="verifiedId")
    var jsverifiedId: String?
        get() = if(::verifiedId.isInitialized) verifiedId else null
        set(v) {
            if(null != v) {
                verifiedId = v
            }
        }
    fun hasVerifiedId() = ::verifiedId.isInitialized
    @Json(ignored = true)
    lateinit var verifiedId: String

    @Json(serializeNull = false, name="tag")
    var jsTag: String?
        get() = if(::tag.isInitialized) tag else null
        set(v) {
            if(null != v) {
                tag = v
            }
        }
    @Json(ignored = true)
    lateinit var tag: String
    fun hasTag() = ::tag.isInitialized

    @Json(serializeNull = false, name="responseTag")
    var jsResponseTag: String?
        get() = if(::responseTag.isInitialized) responseTag else null
        set(v) {
            if(null != v) {
                responseTag = v
            }
        }
    fun hasResponseTag() = ::responseTag.isInitialized
    @Json(ignored = true)
    lateinit var responseTag: String

    fun <T> withSenderAddress(senderAddress: InetSocketAddress): T {
        this.senderAddress = senderAddress
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    fun <T> withVerifiedId(verifiedId: String): T {
        this.verifiedId = verifiedId
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    fun <T> withTag(tag: String): T {
        this.tag = tag
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    fun <T> withResponseTag(responseTag: String): T {
        this.responseTag = responseTag
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    class BaseTypeAdapter : TypeAdapter<BaseCommand> {
        override fun classFor(type: Any): KClass<out BaseCommand> {
            val classNameParts = type.toString().split(".")
                .map { CaseUtils.toCamelCase(it, true, '_') }

            var className = "org.bingle.command.${
                classNameParts[0]
            }"
            if (classNameParts.size == 2) {
                className += "\$" + classNameParts[1]
            }
            return Class.forName(className).kotlin as KClass<out BaseCommand>
        }
    }

    override fun toMap() =
        (Klaxon().parse<Map<String, Any>>(klaxonParser().toJsonString(this))
            ?: throw RuntimeException("could not transform command"))

    fun toJson() = klaxonParser().toJsonString(this)
    override fun equals(other: Any?): Boolean =
        this.toJson().equals((other as BaseCommand).toJson())

    companion object {
        /*        val commandTypeConverter = object : Converter {
                    override fun canConvert(cls: Class<*>) = cls == Ddb.CommandType::class.java

                    override fun toJson(value: Any): String {
                        return "\"${CaseUtils.toCamelCase((value as Ddb.CommandType).toString(), false, '_')}\""
                    }

                    override fun fromJson(jv: JsonValue) = jv.string?.let {
                        Ddb.CommandType.valueOf(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, it))
                    }
                }*/

        val dateConverter = object : Converter {
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")

            init {
                iso.setTimeZone(TimeZone.getTimeZone("UTC"))
            }

            override fun canConvert(cls: Class<*>) = cls == Date::class.java

            override fun toJson(value: Any): String {
                return "\"${iso.format(value as Date)}\""
            }

            override fun fromJson(jv: JsonValue) = jv.string?.let {
                iso.parse(it)
            }
        }

        val inetSocketAddressConverter = object : Converter {
            override fun canConvert(cls: Class<*>) = cls == InetSocketAddress::class.java

            override fun toJson(value: Any): String =
                "\"${value as InetSocketAddress}\""

            override fun fromJson(jv: JsonValue) = jv.string?.let {
                val parts = it.substring(1).split(':')
                InetSocketAddress(parts[0], parts[1].toInt())
            }
        }

        fun klaxonParser() = Klaxon().converter(inetSocketAddressConverter).converter(
            dateConverter
        )

        fun fromMap(mapValues: Map<String, Any?>): BaseCommand {
            val mapJson = Klaxon().toJsonString(mapValues)
            return klaxonParser().parse<BaseCommand>(mapJson)
                ?: throw RuntimeException("could not transform map to command")
        }

        fun <T> fromMap(mapValues: Map<String, Any?>): T = fromMap(mapValues) as T

        fun fromJson(jsonText: String): BaseCommand {
            return klaxonParser().parse<BaseCommand>(jsonText)
                ?: throw RuntimeException("could not transform json to command")
        }

        fun fromJson(jsonBytes: ByteArray) : BaseCommand {
            return fromJson(jsonBytes.decodeToString())
        }
    }

}
