package org.bingle.blockchain.setup

import com.beust.klaxon.Klaxon
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

fun main(argv: Array<String>) {
    if (argv.size != 2) {
        System.err.println("Expects: SetupTool action.json setup/generated/generatedConfig.json")
        System.exit(1)
    }

    val setupAction =
        Klaxon().parse<SetupAction>(Files.readString(Paths.get(argv[0]), StandardCharsets.US_ASCII))
    if(null == setupAction) {
        System.err.println("${argv[0]} could not be read and parsed")
        System.exit(2)
    }

    val generatedConfigText = Files.readString(Paths.get(argv[1]), StandardCharsets.US_ASCII)
    setupAction?.generatedConfig = generatedConfigText?.let { Klaxon().parse<GeneratedConfig>(it) }

    if(null == setupAction?.generatedConfig) {
        System.err.println("${argv[1]} could not be read and parsed")
        System.exit(3)
    }

    System.exit(if (setupAction?.execute() == true) 0 else 1)
}
