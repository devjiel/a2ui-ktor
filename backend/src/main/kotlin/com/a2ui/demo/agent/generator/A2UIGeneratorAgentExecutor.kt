@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.a2ui.demo.agent.generator

import ai.koog.a2a.model.*
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.agents.a2a.core.A2AMessage
import ai.koog.agents.a2a.core.toKoogMessage
import ai.koog.agents.a2a.server.feature.A2AAgentServer
import ai.koog.agents.a2a.server.feature.withA2AAgentServer
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.a2ui.demo.agent.createTask
import com.a2ui.demo.agent.updateTaskStatus
import com.a2ui.demo.agent.updateTaskStatusWithParts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import com.a2ui.demo.business.booking.tools.BookingTools
import com.a2ui.demo.agent.A2UI_GENERATOR_SYSTEM_PROMPT_TEMPLATE

/**
 * AgentExecutor A2A pour la génération A2UI.
 *
 * Graph strategy avec UN SEUL tool (validate_a2ui) et pattern ReAct :
 * LLM → tool call validate_a2ui → tool result → LLM → ... → text response → finish
 *
 * La self-reflection sémantique est dans le prompt, pas dans un tool séparé.
 */
class A2UIGeneratorAgentExecutor(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel,
) : AgentExecutor {

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val agent = createAgent(context, eventProcessor)
        agent.run(context.params.message)
    }

    private fun createAgent(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ): AIAgent<A2AMessage, Unit> {
        // Charger les ressources pour le prompt
        val catalog = loadResource("/catalog/catalog.json")
        val rules = loadResource("/catalog/rules.txt")
        val specSummary = loadResource("/catalog/a2ui_spec_summary.txt")
        val examples = loadResource("/catalog/examples.json")

        val validationTools = A2UIValidationTools()
        val bookingTools = BookingTools()
        val toolRegistry = ToolRegistry {
            tools(validationTools)
            tools(bookingTools)
        }

        return AIAgent(
            promptExecutor = promptExecutor,
            toolRegistry = toolRegistry,
            strategy = strategy<A2AMessage, Unit>("a2ui-generator") {

                // ── Node 1 : Setup — créer la task A2A ──
                val nodeSetup by node<A2AMessage, String> { inputMessage ->
                    val input = inputMessage.toKoogMessage()
                    llm.writeSession {
                        appendPrompt {
                            message(input)
                        }
                    }
                    withA2AAgentServer {
                        createTask(inputMessage, TaskState.Submitted)
                        updateTaskStatus("Génération de l'interface A2UI en cours...", TaskState.Working, final = false)
                    }
                    "" // Return empty string to match nodeLLMRequest input type
                }

                // ── Nodes built-in pour le pattern ReAct tool-calling ──
                val nodeLLMRequest by nodeLLMRequest()
                val nodeExecuteTools by nodeExecuteTools()
                val nodeSendToolResults by nodeLLMSendToolResults()

                // ── Node final : envoyer la réponse A2UI ──
                val nodeProcessResponse by node<String, Unit> { response ->
                    // Strip markdown code fences si le LLM en ajoute malgré le prompt
                    val cleanedResponse = response
                        .trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    try {
                        // Parser le JSON A2UI et envoyer chaque message comme un DataPart séparé
                        val rootJson = Json.parseToJsonElement(cleanedResponse).jsonObject
                        val messages = rootJson["messages"]?.jsonArray ?: buildJsonArray {}

                        val a2uiParts: List<Part> = messages.map { msg ->
                            DataPart(data = msg.jsonObject)
                        }

                        withA2AAgentServer {
                            updateTaskStatusWithParts(
                                a2uiParts.ifEmpty { listOf(DataPart(data = rootJson)) },
                                TaskState.Completed,
                                final = true
                            )
                        }
                    } catch (_: Exception) {
                        // Fallback : envoyer comme TextPart brut si le JSON est malformé
                        withA2AAgentServer {
                            updateTaskStatus(cleanedResponse, TaskState.Completed, final = true)
                        }
                    }
                }

                // ── Edges ──
                edge(nodeStart forwardTo nodeSetup)
                edge(nodeSetup forwardTo nodeLLMRequest)

                // Pattern ReAct : LLM appelle un tool → exécuter → renvoyer résultat au LLM
                edge(nodeLLMRequest forwardTo nodeExecuteTools onToolCalls { true })
                edge(nodeExecuteTools forwardTo nodeSendToolResults)
                // nodeSendToolResults also returns Message.Assistant, so branch same as nodeLLMRequest
                edge(nodeSendToolResults forwardTo nodeExecuteTools onToolCalls { true })
                edge(nodeSendToolResults forwardTo nodeProcessResponse onTextMessage { true })

                // LLM retourne du texte = réponse finale (JSON A2UI validé)
                edge(nodeLLMRequest forwardTo nodeProcessResponse onTextMessage { true })
                edge(nodeProcessResponse forwardTo nodeFinish)
            },
            agentConfig = AIAgentConfig(
                prompt = prompt("a2ui-generator") {
                    system(buildGeneratorSystemPrompt(specSummary, catalog, rules, examples))
                },
                model = model,
                maxAgentIterations = 15
            ),
        ) {
            install(A2AAgentServer) {
                this.context = context
                this.eventProcessor = eventProcessor
            }
        }
    }

    private fun loadResource(path: String): String {
        return this::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()
            ?: error("Resource '$path' non trouvée dans le classpath")
    }

    companion object {
        /**
         * Construit le prompt système du Generator.
         * IMPORTANT : le prompt dit explicitement au LLM de :
         * 1. Toujours utiliser le tool validate_a2ui AVANT de retourner du texte
         * 2. Ne JAMAIS retourner le JSON A2UI en texte AVANT validation
         * 3. Faire une auto-critique sémantique APRÈS validation réussie
         */
        fun buildGeneratorSystemPrompt(
            specSummary: String,
            catalog: String,
            rules: String,
            examples: String
        ): String = A2UI_GENERATOR_SYSTEM_PROMPT_TEMPLATE.format(specSummary, catalog, rules, examples).trimIndent()
    }
}
