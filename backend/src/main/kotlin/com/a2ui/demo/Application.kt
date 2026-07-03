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
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Point d'entrée — Démarre deux serveurs A2A dans le même process.
 *
 * - IntentAgent (/intent) — point d'entrée public, port 9998
 * - A2UIGeneratorAgent (/generator) — agent interne, port 9999
 */
suspend fun main(): Unit = coroutineScope {
    // ── Configuration ──
    val apiKey = System.getenv("OPENROUTER_API_KEY")
        ?: System.getenv("LLM_API_KEY")
        ?: System.getenv("OPENAI_API_KEY")
        ?: error("OPENROUTER_API_KEY non définie.")

    val intentPort = 9998
    val generatorPort = 9999
    val intentPath = "/intent"
    val generatorPath = "/generator"

    // Modèles LLM — utiliser les constantes Koog pour avoir les capabilities correctes
    val intentModel = OpenRouterModels.Gemini2_5Flash
    val generatorModel = OpenRouterModels.Gemini2_5Pro

    // ── Infrastructure LLM (singleton partagé) ──
    val promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenRouter to OpenRouterLLMClient(apiKey)
    )

    // ── 1. A2UIGeneratorAgent (interne, port 9999) ──
    val generatorCard = AgentCard(
        name = "A2UIGeneratorAgent",
        url = "http://localhost:$generatorPort$generatorPath",
        description = "Agent spécialisé dans la génération d'interfaces A2UI v0.9 à partir d'intentions structurées.",
        version = "0.1.0",
        protocolVersion = "0.3.0",
        preferredTransport = TransportProtocol.JSONRPC,
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "a2ui_generation",
                name = "Génération A2UI",
                description = "Génère des messages A2UI valides (createSurface, updateComponents, updateDataModel) à partir d'une intention.",
                tags = listOf("a2ui", "ui-generation", "json")
            )
        )
    )

    val generatorExecutor = A2UIGeneratorAgentExecutor(promptExecutor, generatorModel)
    val generatorServer = A2AServer(agentExecutor = generatorExecutor, agentCard = generatorCard)
    val generatorTransport = HttpJSONRPCServerTransport(generatorServer)

    // ── 2. IntentAgent (public, port 9998) ──
    val intentCard = AgentCard(
        name = "IntentAgent",
        url = "http://localhost:$intentPort$intentPath",
        description = "Agent d'analyse d'intention pour la génération d'interfaces A2UI. Point d'entrée du système multi-agent.",
        version = "0.1.0",
        protocolVersion = "0.3.0",
        preferredTransport = TransportProtocol.JSONRPC,
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "intent_analysis",
                name = "Analyse d'intention A2UI",
                description = "Analyse les demandes utilisateur et génère des interfaces A2UI riches via un agent spécialisé.",
                tags = listOf("intent", "a2ui", "multi-agent")
            )
        )
    )

    val generatorUrl = "http://localhost:$generatorPort$generatorPath"
    val intentExecutor = IntentAgentExecutor(promptExecutor, intentModel, generatorUrl)
    val intentServer = A2AServer(agentExecutor = intentExecutor, agentCard = intentCard)
    val intentTransport = HttpJSONRPCServerTransport(intentServer)

    // ── Démarrage ──
    println("🤖 A2UI Multi-Agent System starting...")
    println("📋 Intent Agent Card: http://localhost:$intentPort/.well-known/agent-card.json")
    println("🔗 Intent Agent endpoint: http://localhost:$intentPort$intentPath")
    println("📋 Generator Agent Card: http://localhost:$generatorPort/.well-known/agent-card.json")
    println("🔗 Generator Agent endpoint: http://localhost:$generatorPort$generatorPath")
    println("---")

    // ── Generator (interne — pas de CORS nécessaire)
    launch {
        generatorTransport.start(
            engineFactory = Netty,
            port = generatorPort,
            path = generatorPath,
            wait = true,
            agentCard = generatorCard,
        )
    }

    // ── Intent Agent (public — serveur Ktor custom avec CORS) ──
    launch {
        try {
            embeddedServer(Netty, port = intentPort) {
                // Installer CORS pour autoriser le frontend Flutter (web)
                install(CORS) {
                    anyHost()
                    allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Options)
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Accept)
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader("X-Requested-With")
                    allowHeader("x-a2a-extensions")
                    allowNonSimpleContentTypes = true
                }

                // SSE requis par transportRoutes() — ContentNegotiation est installé par transportRoutes lui-même
                install(SSE)

                routing {
                    intentTransport.transportRoutes(this, intentPath)
                }
            }.start(wait = true)
        } catch (e: Exception) {
            System.err.println("❌ ERREUR démarrage Intent Agent (port $intentPort): ${e.message}")
            e.printStackTrace()
        }
    }
}
