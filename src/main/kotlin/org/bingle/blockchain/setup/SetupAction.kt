package org.unknown.comms.blockchain.setup

import com.beust.klaxon.Klaxon
import com.beust.klaxon.TypeAdapter
import com.beust.klaxon.TypeFor
import java.io.File
import kotlin.reflect.KClass

class ActionTypeAdapter : TypeAdapter<SetupAction> {
    override fun classFor(type: Any): KClass<out SetupAction> = when(type as String) {
        "deploy" -> DeployAction::class
        "assetCreate" -> AssetCreateAction::class
        "initSwap" -> InitSwapAction::class
        else -> throw IllegalArgumentException("Unknown type: $type")
    }
}

@TypeFor(field = "type", adapter = ActionTypeAdapter::class)
abstract class SetupAction(
    val type: String,
    val passphrase: String
) {
    var generatedConfig: GeneratedConfig? = null

    abstract fun execute() : Boolean

    fun updateGenerated(creatorAddress: String, assetId: Long, appId: Long?) {
        val generatedKotlin = """
            package org.unknown.comms.blockchain.generated

            object AlgoConfig {
                const val assetId = ${assetId}L
                val appId: Long? = ${appId?.let { "${it}L" } ?: "null" }
                const val creatorAddress = "${creatorAddress}"
            }
        """.trimIndent()
        File("src/main/kotlin/org/unknown/comms/blockchain/generated/AlgoConfig.kt")
            .writeText(generatedKotlin)

        val generatedJson = Klaxon().toJsonString(GeneratedConfig(creatorAddress, assetId, appId))
        File("setup/generated/generatedConfig.json")
            .writeText(generatedJson)
    }
}
