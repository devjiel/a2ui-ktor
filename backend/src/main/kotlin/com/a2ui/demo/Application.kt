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
import ai.koog.prompt.llm.LLModel
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Point d'entrée — Démarre deux serveurs A2A dans le même process.
 *
 * - IntentAgent (/intent) — point d'entrée public, port 9998
 * - A2UIGeneratorAgent (/generator) — agent interne, port 9999
 */
suspend fun main(): Unit = coroutineScope {
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

    // Modèles LLM — résolus depuis le YAML
    val intentModel = resolveModel(config.property("intent.model").getString())
    val generatorModel = resolveModel(config.property("generator.model").getString())

    // ── Infrastructure LLM (singleton partagé) ──
    val promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenRouter to OpenRouterLLMClient(apiKey)
    )

    // ── 1. A2UIGeneratorAgent (interne) ──
    val generatorCard = AgentCard(
        name = "A2UIGeneratorAgent",
        url = generatorUrl,
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

    // ── 2. IntentAgent (public) ──
    val intentCard = AgentCard(
        name = "IntentAgent",
        url = intentUrl,
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

    val intentExecutor = IntentAgentExecutor(promptExecutor, intentModel, generatorBaseUrl, generatorUri.path)
    val intentServer = A2AServer(agentExecutor = intentExecutor, agentCard = intentCard)
    val intentTransport = HttpJSONRPCServerTransport(intentServer)

    // ── Démarrage ──
    println("🤖 A2UI Multi-Agent System starting...")
    println("📋 Intent Agent Card: ${intentUri.scheme}://${intentUri.host}:${intentUri.port}/.well-known/agent-card.json")
    println("🔗 Intent Agent endpoint: $intentUrl")
    println("📋 Generator Agent Card: $generatorBaseUrl/.well-known/agent-card.json")
    println("🔗 Generator Agent endpoint: $generatorUrl")
    println("---")

    // ── Generator (interne — pas de CORS nécessaire)
    launch {
        generatorTransport.start(
            engineFactory = Netty,
            port = generatorUri.port,
            path = generatorUri.path,
            wait = true,
            agentCard = generatorCard,
        )
    }

    // ── Intent Agent (public — serveur Ktor custom avec CORS) ──
    launch {
        try {
            embeddedServer(Netty, port = intentUri.port) {
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

                // SSE requis par transportRoutes()
                install(SSE)

                routing {
                    intentTransport.transportRoutes(this, intentUri.path)
                }
            }.start(wait = true)
        } catch (e: Exception) {
            System.err.println("❌ ERREUR démarrage Intent Agent (port ${intentUri.port}): ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * Résout un nom de modèle OpenRouter en [LLModel] avec les capabilities correctes.
 * Les constantes Koog embarquent les metadata (supports tools, structured output, etc.).
 */
private fun resolveModel(modelName: String): LLModel = when (modelName) {
    "google/gemini-2.5-flash" -> OpenRouterModels.Gemini2_5Flash
    "google/gemini-2.5-flash-lite" -> OpenRouterModels.Gemini2_5FlashLite
    "google/gemini-2.5-pro" -> OpenRouterModels.Gemini2_5Pro
    else -> error("Modèle inconnu: '$modelName'. Modèles supportés: google/gemini-2.5-flash, google/gemini-2.5-flash-lite, google/gemini-2.5-pro")
}
