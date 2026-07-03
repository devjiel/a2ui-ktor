plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "com.a2ui.demo"
version = "0.1.0"

application {
    mainClass.set("com.a2ui.demo.ApplicationKt")
}

repositories {
    mavenCentral()
}

val koogVersion = "1.0.0"
val koogA2aVersion = "1.0.0-beta"
val ktorVersion = "3.3.3"

dependencies {
    // ── Koog Agent Framework ──
    implementation("ai.koog:koog-agents:$koogVersion")

    // ── Koog OpenRouter client ──
    implementation("ai.koog:prompt-executor-openrouter-client:$koogVersion")

    // ── Kotlin Serialization JSON ──
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // ── Koog A2A server integration feature ──
    implementation("ai.koog:agents-features-a2a-server:$koogA2aVersion")

    // ── Koog A2A client integration feature ──
    implementation("ai.koog:agents-features-a2a-client:$koogA2aVersion")

    // ── HTTP JSON-RPC transport (server + client) ──
    implementation("ai.koog:a2a-transport-server-jsonrpc-http:$koogA2aVersion")
    implementation("ai.koog:a2a-transport-client-jsonrpc-http:$koogA2aVersion")

    // ── Ktor server + client engines ──
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // ── Logging ──
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // ── Tests ──
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
