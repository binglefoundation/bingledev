plugins {
    kotlin("jvm") version "1.9.0"
}

group = "org.bingle"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.14.1")
    implementation("com.beust:klaxon:5.5")

    implementation("commons-io:commons-io:2.6") // 2.6 is newest for Java 7
    implementation("commons-codec:commons-codec:1.13")
    implementation("org.apache.commons:commons-text:1.10.0")

    implementation("com.google.guava:guava:31.1-jre")

    implementation("com.algorand:algosdk:2.4.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.76")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.76")

    implementation("de.javawi.jstun:jstun:0.7.4")

    implementation("io.github.classgraph:classgraph:4.8.165")

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.21")

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.24.2")
    // https://mvnrepository.com/artifact/io.mockk/mockk
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.lordcodes.turtle:turtle:0.5.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}