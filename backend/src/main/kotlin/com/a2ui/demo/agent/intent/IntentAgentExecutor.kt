@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.a2ui.demo.agent.intent

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.model.*
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
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
import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.Part
import com.a2ui.demo.agent.updateTaskStatusWithParts
import kotlin.uuid.Uuid

/**
 * AgentExecutor A2A — Point d'entrée public.
 * Analyse l'intention utilisateur, valide le JSON, puis délègue au GeneratorAgent via A2A.
 *
 * Graph : LLM → tool-calling loop (validate_intent) → réponse texte (intent JSON validé)
 *         → nodeForwardToGenerator → A2A call au GeneratorAgent → réponse finale
 */
class IntentAgentExecutor(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel,
    private val generatorUrl: String, // URL du GeneratorAgent A2A endpoint
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
        val validationTools = IntentValidationTools()
        val toolRegistry = ToolRegistry {
            tools(validationTools)
        }

        return AIAgent(
            promptExecutor = promptExecutor,
            toolRegistry = toolRegistry,
            strategy = strategy<A2AMessage, Unit>("intent-agent") {

                // ── Node 1 : Setup ──
                val nodeSetup by node<A2AMessage, String> { inputMessage ->
                    val input = inputMessage.toKoogMessage()
                    llm.writeSession {
                        appendPrompt {
                            message(input)
                        }
                    }
                    withA2AAgentServer {
                        createTask(inputMessage, TaskState.Submitted)
                        updateTaskStatus("Analyse de l'intention en cours...", TaskState.Working, final = false)
                    }
                    "" // Return empty string to match nodeLLMRequest input type (String)
                }

                // ── Node 2 : LLM request (tool-calling loop) ──
                val nodeLLMRequest by nodeLLMRequest()
                val nodeExecuteTools by nodeExecuteTools()
                val nodeSendToolResults by nodeLLMSendToolResults()

                // ── Node 3 : Forward validated intent to GeneratorAgent via A2A ──
                val nodeForwardToGenerator by node<String, Unit> { intentJson ->
                    withA2AAgentServer {
                        updateTaskStatus("Intention analysée. Génération de l'interface A2UI...", TaskState.Working, final = false)
                    }

                    val baseUrl = generatorUrl.substringBeforeLast("/")
                    val transport = HttpJSONRPCClientTransport(url = generatorUrl)
                    try {
                        val generatorMessage = "Génère une interface A2UI pour l'intention suivante:\n$intentJson"

                        val message = Message(
                            messageId = Uuid.random().toString(),
                            role = Role.User,
                            parts = listOf(TextPart(generatorMessage)),
                        )
                        val params = MessageSendParams(message = message)
                        val request = Request(data = params)

                        val agentCardResolver = UrlAgentCardResolver(baseUrl = baseUrl)
                        val client = A2AClient(transport, agentCardResolver)
                        client.connect() // Résoudre l'agent card AVANT le streaming

                        // Utiliser sendMessageStreaming pour attendre la réponse complète du Generator
                        var finalParts: List<Part> = emptyList()
                        client.sendMessageStreaming(request).collect { response ->
                            val event = response.data
                            if (event is TaskStatusUpdateEvent && event.final == true) {
                                // C'est l'événement final — extraire les parts
                                finalParts = event.status.message?.parts?.toList() ?: emptyList()
                            }
                        }

                        if (finalParts.isEmpty()) {
                            finalParts = listOf(TextPart("Erreur: le GeneratorAgent n'a pas retourné de contenu"))
                        }

                        withA2AAgentServer {
                            updateTaskStatusWithParts(finalParts, TaskState.Completed, final = true)
                        }
                    } catch (e: Exception) {
                        withA2AAgentServer {
                            updateTaskStatus(
                                "Erreur lors de l'appel au GeneratorAgent: ${e.message}",
                                TaskState.Failed,
                                final = true
                            )
                        }
                    } finally {
                        transport.close()
                    }
                }

                // ── Edges ──
                edge(nodeStart forwardTo nodeSetup)
                edge(nodeSetup forwardTo nodeLLMRequest)

                // Tool-calling loop
                edge(nodeLLMRequest forwardTo nodeExecuteTools onToolCalls { true })
                edge(nodeExecuteTools forwardTo nodeSendToolResults)
                // nodeSendToolResults also returns Message.Assistant — branch same as nodeLLMRequest
                edge(nodeSendToolResults forwardTo nodeExecuteTools onToolCalls { true })
                edge(nodeSendToolResults forwardTo nodeForwardToGenerator onTextMessage { true })

                // Réponse texte → forward au generator
                edge(nodeLLMRequest forwardTo nodeForwardToGenerator onTextMessage { true })
                edge(nodeForwardToGenerator forwardTo nodeFinish)
            },
            agentConfig = AIAgentConfig(
                prompt = prompt("intent-agent") {
                    system("""
Tu es un agent d'analyse d'intention pour la génération d'interfaces A2UI.

## TA MISSION
Analyse le message de l'utilisateur pour comprendre quelle interface il souhaite.

## PROCESSUS
1. Analyse le message utilisateur
2. Détermine l'intention et le type d'UI adapté
3. Génère un JSON structuré d'intention
4. Appelle le tool validate_intent pour vérifier ton JSON
5. Si la validation échoue, corrige et re-valide
6. Quand la validation réussit, retourne le JSON validé (UNIQUEMENT le JSON, rien d'autre)

## FORMAT DE SORTIE
{
  "userMessage": "le message original de l'utilisateur",
  "intent": "description concise et actionnable de ce que l'utilisateur veut",
  "justification": "pourquoi tu penses que c'est l'intention",
  "uiType": "form|card|list|dashboard|info|player|notification|profile|compose|detail|status|settings|login|order"
}

## RÈGLES
- "intent" doit être actionnable et spécifique
- "uiType" doit correspondre au pattern d'UI le plus adapté
- Retourne UNIQUEMENT le JSON validé, pas de texte avant ou après
- Appelle TOUJOURS validate_intent avant de retourner ta réponse finale
                    """.trimIndent())
                },
                model = model,
                maxAgentIterations = 30
            ),
        ) {
            install(A2AAgentServer) {
                this.context = context
                this.eventProcessor = eventProcessor
            }
        }
    }
}
