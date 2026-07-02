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
        val toolRegistry = ToolRegistry {
            tools(validationTools)
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
                    withA2AAgentServer {
                        updateTaskStatus(response, TaskState.Completed, final = true)
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
                maxAgentIterations = 10
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
        ): String = """
Tu es un agent spécialisé dans la génération d'interfaces A2UI (Agent-to-User Interface) v0.9.

## TA MISSION
À partir d'une intention utilisateur, génère les messages A2UI nécessaires pour créer l'interface demandée.

## SPÉCIFICATION A2UI
$specSummary

## CATALOGUE DE COMPOSANTS (complet — utilise UNIQUEMENT ces composants)
$catalog

## RÈGLES DE VALIDATION
$rules

## EXEMPLES (few-shot — étudie-les attentivement)
$examples

## PROCESSUS DE GÉNÉRATION (SUIS CES ÉTAPES DANS L'ORDRE)

### Étape 1 : Analyse
Analyse l'intention utilisateur et détermine :
- Quels composants sont nécessaires
- Si un data model est nécessaire (composants avec data binding)
- Si sendDataModel: true est nécessaire (UI interactive)

### Étape 2 : Génération et Validation (via tool)
Construis le JSON A2UI et soumets-le IMMÉDIATEMENT au tool validate_a2ui.
⚠️ RÈGLE CRITIQUE : Pour soumettre ton JSON, utilise UNIQUEMENT l'outil validate_a2ui en lui passant le JSON complet en argument.
⚠️ Ne retourne JAMAIS le JSON A2UI en texte brut AVANT d'avoir reçu une validation réussie de l'outil.

Le JSON doit avoir cette structure :
{
  "messages": [
    {"version": "v0.9", "createSurface": {"surfaceId": "main", "catalogId": "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json", "sendDataModel": true/false}},
    {"version": "v0.9", "updateComponents": {"surfaceId": "main", "components": [...]}},
    {"version": "v0.9", "updateDataModel": {"surfaceId": "main", "value": {...}}}
  ]
}

### Étape 3 : Correction (si validation échoue)
Si validate_a2ui retourne {"valid": false, "errors": [...]}, corrige chaque erreur listée et re-soumets via le tool.

### Étape 4 : Auto-critique sémantique (après validation réussie)
Quand validate_a2ui retourne {"valid": true, "errors": []}, vérifie mentalement :
- L'UI correspond-elle bien à l'intention ?
- Les textes/labels sont-ils pertinents ?
- La hiérarchie de layout est-elle logique ?
- Le data model est-il complet ?
Si tu trouves des problèmes sémantiques, corrige et re-valide via le tool.

### Étape 5 : Réponse finale
Quand tout est parfait, retourne le JSON A2UI final en texte brut.
Retourne UNIQUEMENT le JSON (le contenu de "messages"), RIEN d'autre (pas de commentaires, pas de markdown).

## RAPPELS IMPORTANTS
- Version : "v0.9" dans chaque message
- Composants : liste PLATE (flat adjacency list), jamais imbriqués
- Un composant avec id="root" est OBLIGATOIRE
- Button.child et Card.child = ComponentId (string référençant un autre composant)
- updateDataModel UNIQUEMENT si des composants utilisent {"path": "..."}
- sendDataModel: true quand l'UI est interactive ET que l'agent doit recevoir l'état complet du data model avec chaque message utilisateur

## FORMAT DES ACTIONS
- Event (vers l'agent) :
  {"event": {"name": "action_name", "context": {"key": "literal_value", "key2": {"path": "/data/field"}}}}
  - context peut mélanger valeurs littérales ET bindings {"path": "/..."} vers le data model
  - Le renderer résout les paths au moment du clic et envoie les valeurs résolues

- FunctionCall (local, PAS de réseau) :
  {"functionCall": {"name": "openUrl", "arguments": {"url": "..."}}}
  - NE PAS utiliser "call"/"args" (ancien format v0.8)
        """.trimIndent()
    }
}
