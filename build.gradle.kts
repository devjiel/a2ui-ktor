plugins {
    kotlin("jvm") version "2.2.0"
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
val ktorVersion = "3.1.3"

dependencies {
    // ── Koog Agent Framework ──
    implementation("ai.koog:koog-agents:$koogVersion")

    // ── Koog OpenRouter client ──
    // Fournit : OpenRouterLLMClient, OpenRouterModels
    implementation("ai.koog:prompt-executor-openrouter-client:$koogVersion")

    // ── Koog A2A server integration feature ──
    // Fournit : A2AAgentServer feature, withA2AAgentServer()
    // Tire a2a-server (AgentExecutor, A2AServer, AgentCard...) en transitif
    implementation("ai.koog:agents-features-a2a-server:$koogA2aVersion")

    // ── HTTP JSON-RPC transport ──
    // Fournit : HttpJSONRPCServerTransport
    implementation("ai.koog:a2a-transport-server-jsonrpc-http:$koogA2aVersion")

    // ── Ktor server engine ──
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    // ── Logging ──
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // ── Tests ──
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
