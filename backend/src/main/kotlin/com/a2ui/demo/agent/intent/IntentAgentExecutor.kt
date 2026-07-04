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
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.llm.LLModel
import com.a2ui.demo.agent.*
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * AgentExecutor A2A — Point d'entrée public.
 * Analyse l'intention utilisateur via Structured Output, puis délègue au GeneratorAgent via A2A.
 */
class IntentAgentExecutor(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel,
    private val generatorBaseUrl: String,
    private val generatorPath: String,
) : AgentExecutor {

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val userMessage = context.params.message
        sendTaskCreated(context, eventProcessor, userMessage, TaskState.Submitted)
        sendStatusUpdate(context, eventProcessor, "Analyse de l'intention...", TaskState.Working, final = false)

        // Structured output — un seul appel LLM
        val result = promptExecutor.executeStructured<IntentResult>(
            prompt = prompt("intent-analysis") {
                system(INTENT_AGENT_SYSTEM_PROMPT)
                user(extractUserText(userMessage))
            },
            model = model,
            examples = INTENT_EXAMPLES,
            fixingParser = StructureFixingParser(model = model, retries = 3)
        )

        val intentResult = result.getOrElse { error ->
            sendStatusUpdate(context, eventProcessor, "Erreur lors de l'analyse: ${error.message}", TaskState.Failed, final = true)
            return
        }

        forwardToGenerator(Json.encodeToString(intentResult.data), context, eventProcessor)
    }

    /**
     * Forward l'intention au GeneratorAgent via A2A et relay chaque event vers le client Flutter.
     */
    private suspend fun forwardToGenerator(
        intentJson: String,
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        sendStatusUpdate(context, eventProcessor, "Intention analysée. Génération de l'interface A2UI...", TaskState.Working, final = false)

        val transport = HttpJSONRPCClientTransport(url = "$generatorBaseUrl$generatorPath")
        try {
            val client = A2AClient(transport, UrlAgentCardResolver(baseUrl = generatorBaseUrl))
            client.connect()

            client.sendMessageStreaming(buildGeneratorRequest(intentJson)).collect { response ->
                (response.data as? TaskStatusUpdateEvent)?.let { forwardEvent(it, context, eventProcessor) }
            }
        } catch (e: Exception) {
            sendStatusUpdate(context, eventProcessor, "Erreur lors de l'appel au GeneratorAgent: ${e.message}", TaskState.Failed, final = true)
        } finally {
            transport.close()
        }
    }

    /**
     * Relay un [TaskStatusUpdateEvent] du Generator vers le client Flutter.
     * - final=true  → forward les DataParts (réponse A2UI) et ferme le stream
     * - final=false → forward le texte de status intermédiaire
     */
    private suspend fun forwardEvent(
        event: TaskStatusUpdateEvent,
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        if (event.final) {
            val parts = event.status.message?.parts?.toList()
                ?: listOf(TextPart("Erreur: réponse vide du GeneratorAgent"))
            sendStatusUpdateWithParts(context, eventProcessor, parts, TaskState.Completed, final = true)
        } else {
            val text = event.status.message?.parts
                ?.filterIsInstance<TextPart>()
                ?.joinToString("\n") { it.text }
            if (!text.isNullOrBlank()) {
                sendStatusUpdate(context, eventProcessor, text, TaskState.Working, final = false)
            }
        }
    }

    private fun buildGeneratorRequest(intentJson: String): Request<MessageSendParams> {
        val message = Message(
            messageId = Uuid.random().toString(),
            role = Role.User,
            parts = listOf(TextPart("Génère une interface A2UI pour l'intention suivante:\n$intentJson")),
        )
        return Request(data = MessageSendParams(message = message))
    }

    private fun extractUserText(message: Message): String =
        message.parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }
}
