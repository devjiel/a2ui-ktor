package com.a2ui.demo

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import com.a2ui.demo.agent.HelloAgentExecutor
import io.ktor.server.netty.Netty

/**
 * Point d'entrée — Démarre le serveur A2A.
 *
 * Responsabilités (infrastructure + wiring) :
 * - Configuration (clés API, port, model)
 * - Création du PromptExecutor partagé
 * - Définition de l'AgentCard (identité A2A)
 * - Instanciation de l'AgentExecutor avec ses dépendances
 * - Wiring A2AServer + Transport
 */
suspend fun main() {
    // ── Configuration ──
    val apiKey = System.getenv("OPENROUTER_API_KEY")
        ?: System.getenv("LLM_API_KEY")
        ?: System.getenv("OPENAI_API_KEY")
        ?: error("OPENROUTER_API_KEY non définie.")

    val port = 9998
    val path = "/hello"
    val model = OpenRouterModels.GPT4o

    // ── Infrastructure LLM (singleton partagé) ──
    val promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenRouter to OpenRouterLLMClient(apiKey)
    )

    // ── AgentCard — identité et capacités de l'agent ──
    val agentCard = AgentCard(
        name = "HelloAgent",
        url = "http://localhost:$port$path",
        description = "Un agent IA conversationnel propulsé par Koog, exposé via le protocole A2A.",
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

    // ── Agent — executor avec ses dépendances injectées ──
    val agentExecutor = HelloAgentExecutor(promptExecutor, model)

    // ── A2AServer + Transport ──
    val server = A2AServer(agentExecutor = agentExecutor, agentCard = agentCard)
    val transport = HttpJSONRPCServerTransport(server)

    println("🤖 A2A Agent Server starting...")
    println("📋 Agent Card: http://localhost:$port/.well-known/agent-card.json")
    println("🔗 Agent endpoint: http://localhost:$port$path")
    println("---")

    transport.start(
        engineFactory = Netty,
        port = port,
        path = path,
        wait = true
    )
}
