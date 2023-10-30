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
    implementation("commons-codec:commons-codec:1.12")
    implementation("org.apache.commons:commons-text:1.10.0")

    implementation("com.google.guava:guava:31.1-jre")

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}