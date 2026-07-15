@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

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
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Point d'entrée A2A pour l'analyse d'intention et le routage.
 *
 * Deux modes de fonctionnement :
 * 1. **Message initial** (texte libre) → analyse d'intention structurée, puis délégation au GeneratorAgent.
 * 2. **Action de continuation** (bouton A2UI) → bypass de l'analyse, forward direct avec le contexte résolu.
 *
 * L'architecture est **stateless** : le client Flutter GenUI renvoie le contexte résolu
 * dans chaque [DataPart] d'action, éliminant le besoin de mémoire côté serveur.
 */
class IntentAgentExecutor(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel,
    private val generatorBaseUrl: String,
    private val generatorPath: String,
) : AgentExecutor {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        val userMessage = context.params.message
        sendTaskCreated(context, eventProcessor, userMessage, TaskState.Submitted)

        val clientDataModel = extractClientDataModel(context)
        val eventInfo = detectA2UIAction(userMessage)

        val lang = extractLanguage(userMessage, clientDataModel)

        if (eventInfo != null) {
            handleContinuation(eventInfo, clientDataModel, lang, userMessage, context, eventProcessor)
        } else {
            handleInitialMessage(userMessage, clientDataModel, lang, context, eventProcessor)
        }
    }

    // ── Handlers ────────────────────────────────────────────────────

    private suspend fun handleContinuation(
        eventInfo: A2UIActionInfo,
        clientDataModel: JsonObject?,
        lang: String,
        userMessage: Message,
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        sendStatusUpdate(
            context, eventProcessor,
            "Processing action '${eventInfo.name}'...",
            TaskState.Working, final = false,
        )
        val prompt = continuationPrompt(
            eventName = eventInfo.name,
            context = eventInfo.context?.toString().orEmpty(),
            dataModel = clientDataModel?.toString().orEmpty(),
            userText = extractUserText(userMessage),
        )
        forwardToGenerator(prompt, lang, context, eventProcessor)
    }

    private suspend fun handleInitialMessage(
        userMessage: Message,
        clientDataModel: JsonObject?,
        lang: String,
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        sendStatusUpdate(
            context, eventProcessor,
            "Analyzing intent...",
            TaskState.Working, final = false,
        )

        val cleanText = stripLangTag(extractUserText(userMessage))

        val result = promptExecutor.executeStructured<IntentResult>(
            prompt = prompt("intent-analysis") {
                system(INTENT_AGENT_SYSTEM_PROMPT)
                user(cleanText)
            },
            model = model,
            examples = INTENT_EXAMPLES,
            fixingParser = StructureFixingParser(model = model, retries = 3),
        )

        val intentResult = result.getOrElse { error ->
            sendStatusUpdate(
                context, eventProcessor,
                "Intent analysis error: ${error.message}",
                TaskState.Failed, final = true,
            )
            return
        }

        val prompt = initialPrompt(
            intentJson = Json.encodeToString(intentResult.data),
            dataModel = clientDataModel?.toString().orEmpty(),
        )
        forwardToGenerator(prompt, lang, context, eventProcessor)
    }

    // ── A2UI action detection ───────────────────────────────────────

    /**
     * Informations extraites d'une action A2UI déclenchée par un bouton.
     *
     * @property name      Nom de l'event (ex: `search_flights`, `confirm_booking`).
     * @property context   Valeurs résolues des champs du formulaire au moment du clic.
     * @property rawData   JSON brut complet du message (pour forward si nécessaire).
     */
    private data class A2UIActionInfo(
        val name: String,
        val context: JsonObject?,
        val rawData: JsonObject,
    )

    /**
     * Détecte si le message contient une action A2UI (clic bouton) plutôt qu'un message texte libre.
     *
     * Cherche d'abord dans les [DataPart] (format structuré envoyé par le client Flutter GenUI),
     * puis tente de parser les [TextPart] comme JSON en fallback.
     */
    private fun detectA2UIAction(message: Message): A2UIActionInfo? {
        for (part in message.parts) {
            when (part) {
                is DataPart -> extractActionFromJson(part.data)?.let { return it }
                is TextPart -> tryParseJsonAction(part.text)?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    /**
     * Tente de parser un texte comme un JSON d'action A2UI.
     * Retourne `null` silencieusement si le texte n'est pas du JSON valide.
     */
    private fun tryParseJsonAction(text: String): A2UIActionInfo? = try {
        val element = json.parseToJsonElement(text)
        (element as? JsonObject)?.let(::extractActionFromJson)
    } catch (_: Exception) {
        null
    }

    /**
     * Extrait les infos d'action depuis un [JsonObject].
     *
     * Trois formats supportés, dans l'ordre de priorité :
     *
     * 1. **Flutter GenUI** (réel, observé en production) :
     *    ```json
     *    {"version":"v0.9", "action":{"name":"search_flights", "context":{...}, "sourceComponentId":"...", "surfaceId":"..."}}
     *    ```
     *
     * 2. **Spec A2UI** (format event défini dans la spécification) :
     *    ```json
     *    {"event":{"name":"search_flights", "context":{...}}}
     *    ```
     *
     * 3. **Flat** (format simplifié, fallback) :
     *    ```json
     *    {"name":"search_flights", "context":{...}}
     *    ```
     */
    private fun extractActionFromJson(data: JsonObject): A2UIActionInfo? =
        extractFromKey(data, "action")
            ?: extractFromKey(data, "event")
            ?: extractFlat(data)

    private fun extractFromKey(data: JsonObject, key: String): A2UIActionInfo? {
        val obj = data[key] as? JsonObject ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        return A2UIActionInfo(
            name = name,
            context = obj["context"] as? JsonObject,
            rawData = data,
        )
    }

    private fun extractFlat(data: JsonObject): A2UIActionInfo? {
        val name = data["name"]?.jsonPrimitive?.contentOrNull ?: return null
        if ("context" !in data) return null
        return A2UIActionInfo(
            name = name,
            context = data["context"] as? JsonObject,
            rawData = data,
        )
    }

    // ── Client data model extraction ────────────────────────────────

    /**
     * Extrait le `a2uiClientDataModel` des metadata A2A.
     *
     * Quand `sendDataModel: true` est activé sur la surface, le client Flutter peut
     * attacher l'état complet du data model dans les metadata du message.
     *
     * Cherche dans `MessageSendParams.metadata` (transport-level) puis dans
     * `Message.metadata` (message-level) en fallback.
     */
    private fun extractClientDataModel(context: RequestContext<MessageSendParams>): JsonObject? =
        context.params.metadata?.get("a2uiClientDataModel") as? JsonObject
            ?: context.params.message.metadata?.get("a2uiClientDataModel") as? JsonObject

    // ── Language extraction ─────────────────────────────────────────

    /**
     * Extracts the user's preferred language code.
     *
     * Priority:
     * 1. clientDataModel["lang"] — used on continuations (GeneratorAgent stores "lang" in updateDataModel)
     * 2. [LANG:xx] tag in message text — used on initial messages (frontend injects it)
     * 3. Fallback: "en"
     */
    private fun extractLanguage(message: Message, clientDataModel: JsonObject?): String {
        // 1. From data model (continuations)
        clientDataModel?.get("lang")?.jsonPrimitive?.contentOrNull?.let { return it }

        // 2. From message text (initial messages)
        val text = extractUserText(message)
        val match = Regex("""\[LANG:([a-zA-Z-]+)]""").find(text)
        if (match != null) return match.groupValues[1]

        // 3. Fallback
        return "en"
    }

    /**
     * Strips the [LANG:xx] tag from message text so the LLM doesn't see it.
     */
    private fun stripLangTag(text: String): String =
        text.replace(Regex("""\[LANG:[a-zA-Z-]+]\n?"""), "").trim()

    // ── Generator forwarding ────────────────────────────────────────

    /**
     * Forward le prompt au GeneratorAgent via A2A et relay chaque event vers le client Flutter.
     */
    private suspend fun forwardToGenerator(
        prompt: String,
        lang: String,
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        sendStatusUpdate(
            context, eventProcessor,
            "Generating A2UI interface...",
            TaskState.Working, final = false,
        )

        val transport = HttpJSONRPCClientTransport(url = "$generatorBaseUrl$generatorPath")
        try {
            val client = A2AClient(transport, UrlAgentCardResolver(baseUrl = generatorBaseUrl))
            client.connect()

            client.sendMessageStreaming(buildGeneratorRequest(prompt, lang)).collect { response ->
                (response.data as? TaskStatusUpdateEvent)?.let {
                    forwardEvent(it, context, eventProcessor)
                }
            }
        } catch (e: Exception) {
            logger.error("GeneratorAgent call error", e)
            sendStatusUpdate(
                context, eventProcessor,
                "GeneratorAgent call error: ${e.message}",
                TaskState.Failed, final = true,
            )
        } finally {
            transport.close()
        }
    }

    /**
     * Relay un [TaskStatusUpdateEvent] du Generator vers le client Flutter.
     */
    private suspend fun forwardEvent(
        event: TaskStatusUpdateEvent,
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        if (event.final) {
            val parts = event.status.message?.parts?.toList()
                ?: listOf(TextPart("Error: empty response from GeneratorAgent"))
            sendStatusUpdateWithParts(context, eventProcessor, parts, TaskState.Completed, final = true)
        } else {
            event.status.message?.parts
                ?.filterIsInstance<TextPart>()
                ?.joinToString("\n") { it.text }
                ?.takeIf { it.isNotBlank() }
                ?.let { text ->
                    sendStatusUpdate(context, eventProcessor, text, TaskState.Working, final = false)
                }
        }
    }

    private fun buildGeneratorRequest(prompt: String, lang: String): Request<MessageSendParams> {
        val message = Message(
            messageId = Uuid.random().toString(),
            role = Role.User,
            parts = listOf(TextPart(prompt)),
        )
        val metadata = buildJsonObject { put("lang", lang) }
        return Request(data = MessageSendParams(message = message, metadata = metadata))
    }

    private fun extractUserText(message: Message): String =
        message.parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }

    private companion object {
        private val logger = LoggerFactory.getLogger(IntentAgentExecutor::class.java)
    }
}
