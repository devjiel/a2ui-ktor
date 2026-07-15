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
import com.a2ui.demo.agent.generator.A2UIGeneratorAgentExecutor
import com.a2ui.demo.agent.intent.IntentAgentExecutor
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Point d'entrée — Démarre deux serveurs A2A dans le même process.
 *
 * - IntentAgent (/intent) — point d'entrée public, port 9998
 * - A2UIGeneratorAgent (/generator) — agent interne, port 9999
 */
suspend fun main(): Unit = coroutineScope {
    val logger = LoggerFactory.getLogger("com.a2ui.demo.Application")

    // ── Configuration ──
    val config = ApplicationConfig("application.yaml")

    val apiKey = System.getenv("OPENROUTER_API_KEY")
        ?: System.getenv("LLM_API_KEY")
        ?: System.getenv("OPENAI_API_KEY")
        ?: error("OPENROUTER_API_KEY non définie.")

    val intentUrl = config.property("intent.url").getString()
    val generatorUrl = config.property("generator.url").getString()

    val intentUri = URI(intentUrl)
    val generatorUri = URI(generatorUrl)

    val generatorBaseUrl = "${generatorUri.scheme}://${generatorUri.host}:${generatorUri.port}"

    // Modèles LLM
    val intentModel = OpenRouterModels.Gemini2_5Flash
    val generatorModel = OpenRouterModels.Gemini2_5Flash

    // ── Infrastructure LLM (singleton partagé) ──
    val promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenRouter to OpenRouterLLMClient(apiKey)
    )

    // ── 1. A2UIGeneratorAgent (interne) ──
    val generatorCard = AgentCard(
        name = "A2UIGeneratorAgent",
        url = generatorUrl,
        description = "Specialized agent for generating A2UI v0.9 interfaces from structured intents.",
        version = "0.1.0",
        protocolVersion = "0.3.0",
        preferredTransport = TransportProtocol.JSONRPC,
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "a2ui_generation",
                name = "A2UI Generation",
                description = "Generates valid A2UI messages (createSurface, updateComponents, updateDataModel) from an intent.",
                tags = listOf("a2ui", "ui-generation", "json")
            )
        )
    )

    val generatorExecutor = A2UIGeneratorAgentExecutor(promptExecutor, generatorModel)
    val generatorServer = A2AServer(agentExecutor = generatorExecutor, agentCard = generatorCard)
    val generatorTransport = HttpJSONRPCServerTransport(generatorServer)

    // ── 2. IntentAgent (public) ──
    val intentCard = AgentCard(
        name = "IntentAgent",
        url = intentUrl,
        description = "Intent analysis agent for A2UI interface generation. Entry point of the multi-agent system.",
        version = "0.1.0",
        protocolVersion = "0.3.0",
        preferredTransport = TransportProtocol.JSONRPC,
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "intent_analysis",
                name = "A2UI Intent Analysis",
                description = "Analyzes user requests and generates rich A2UI interfaces via a specialized agent.",
                tags = listOf("intent", "a2ui", "multi-agent")
            )
        )
    )

    val intentExecutor = IntentAgentExecutor(promptExecutor, intentModel, generatorBaseUrl, generatorUri.path)
    val intentServer = A2AServer(agentExecutor = intentExecutor, agentCard = intentCard)
    val intentTransport = HttpJSONRPCServerTransport(intentServer)

    // ── Démarrage ──
    logger.info("🤖 A2UI Multi-Agent System starting...")
    logger.info("📋 Intent Agent Card: ${intentUri.scheme}://${intentUri.host}:${intentUri.port}/.well-known/agent-card.json")
    logger.info("🔗 Intent Agent endpoint: $intentUrl")
    logger.info("📋 Generator Agent Card: $generatorBaseUrl/.well-known/agent-card.json")
    logger.info("🔗 Generator Agent endpoint: $generatorUrl")
    logger.info("---")

    // ── Generator (interne — pas de CORS nécessaire)
    generatorTransport.start(
        engineFactory = Netty,
        port = generatorUri.port,
        path = generatorUri.path,
        wait = false,
        agentCard = generatorCard,
    )

    // ── Intent Agent (public — serveur Ktor custom avec CORS) ──
    try {
        embeddedServer(Netty, port = intentUri.port) {
            // Installer CORS pour autoriser le frontend Flutter (web)
            install(CORS) {
                anyHost() // NOTE: À configurer avec le domaine frontend en production
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Accept)
                allowHeader(HttpHeaders.Authorization)
                allowHeader("X-Requested-With")
                allowHeader("x-a2a-extensions")
                allowNonSimpleContentTypes = true
            }

            // SSE requis par transportRoutes()
            install(SSE)

            routing {
                intentTransport.transportRoutes(this, intentUri.path)
            }
        }.start(wait = false)
    } catch (e: Exception) {
        logger.error("❌ ERREUR démarrage Intent Agent (port ${intentUri.port}): ${e.message}", e)
    }

    // Garde le processus actif sans bloquer de thread
    awaitCancellation()
}
