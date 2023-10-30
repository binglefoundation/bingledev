package org.bingle.command

import com.beust.klaxon.*
import com.google.common.base.CaseFormat
import org.apache.commons.text.CaseUtils

import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KClass

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
open class BaseCommand( ) : ISendableCommand {

    val type = this.javaClass.name.replace("org.bingle.command.", "").split("$")
        .map { CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, it)}.joinToString(".")
    class PlainCommand(val text: String) : BaseCommand()

    class BaseTypeAdapter : TypeAdapter<BaseCommand> {
        override fun classFor(type: Any): KClass<out BaseCommand> {
            val (rootClassName, subClassName) = type.toString().split(".")
                .map { CaseUtils.toCamelCase(it, true, '_') }

            val className = "org.bingle.command.${rootClassName}\$${subClassName}"
            val klass = Class.forName(className).kotlin as? KClass<out BaseCommand>
                ?: throw IllegalArgumentException("BaseTypeAdapter::classFor ${type} does not define a class")
            return klass
        }
    }

    override fun toMap() =
        (Klaxon().parse<Map<String, Any>>(klaxonParser().toJsonString(this))
            ?: throw RuntimeException("could not transform command"))

    fun toJson() = klaxonParser().toJsonString(this)
    override fun equals(other: Any?): Boolean =
        this.toJson() .equals( (other as BaseCommand).toJson())

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

        fun fromJson(jsonText: String): BaseCommand {
            return klaxonParser().parse<BaseCommand>(jsonText)
                ?: throw RuntimeException("could not transform json to command")
        }
    }

}
