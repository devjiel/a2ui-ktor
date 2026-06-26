package com.a2ui.demo

// ── Protocole A2A ──
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.TransportProtocol

// ── Transport ──
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport

// ── Ktor ──
import io.ktor.server.netty.Netty

// ── Agent ──
import com.a2ui.demo.agent.HelloAgentExecutor

/**
 * Point d'entrée — Démarre un serveur A2A.
 *
 * Code calqué sur la doc officielle :
 * val server = A2AServer(agentExecutor = ..., agentCard = ...)
 * val transport = HttpJSONRPCServerTransport(server)
 * transport.start(engineFactory = Netty, port = ..., path = ..., wait = true)
 */
suspend fun main() {
    // 1. Clé API OpenRouter
    val apiKey = System.getenv("OPENROUTER_API_KEY")
        ?: System.getenv("LLM_API_KEY")
        ?: System.getenv("OPENAI_API_KEY")
        ?: error("OPENROUTER_API_KEY non définie.")

    // 2. AgentCard — exactement comme la doc officielle
    val agentCard = AgentCard(
        name = "HelloAgent",
        url = "http://localhost:9998/hello",
        description = "Un agent IA simple propulsé par Koog et OpenRouter, exposé via le protocole A2A.",
        version = "0.1.0",
        protocolVersion = "0.3.0",
        preferredTransport = TransportProtocol.JSONRPC,
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "general_chat",
                name = "Conversation générale",
                description = "Répond aux questions et assiste l'utilisateur en français.",
                tags = listOf("chat", "assistant", "french")
            )
        )
    )

    // 3. AgentExecutor — OpenRouter natif
    val agentExecutor = HelloAgentExecutor(apiKey)

    // 4. A2AServer — exactly as documented
    val server = A2AServer(agentExecutor = agentExecutor, agentCard = agentCard)

    // 5. Transport — exactly as documented
    val transport = HttpJSONRPCServerTransport(server)

    println("🤖 A2A Agent Server starting...")
    println("📋 Agent Card: http://localhost:9998/.well-known/agent-card.json")
    println("🔗 Agent endpoint: http://localhost:9998/hello")
    println("---")

    // 6. Start — exactly as documented (no agentCard here)
    transport.start(
        engineFactory = Netty,
        port = 9998,
        path = "/hello",
        wait = true
    )
}
