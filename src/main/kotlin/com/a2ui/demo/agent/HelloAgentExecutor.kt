package com.a2ui.demo.agent

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
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.utils.time.KoogClock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * AgentExecutor A2A qui crée un AIAgent Koog avec graph strategy.
 *
 * Supporte message/send (synchrone) et message/stream (SSE).
 * Utilise TaskStatusUpdateEvent avec final=true/false pour le streaming.
 */
@OptIn(ExperimentalUuidApi::class)
class HelloAgentExecutor(
    private val apiKey: String
) : AgentExecutor {

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val agent = createAgent(context, eventProcessor)
        agent.run(context.params.message)
    }

    @OptIn(ExperimentalTime::class)
    private fun createAgent(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(
            LLMProvider.OpenRouter to OpenRouterLLMClient(apiKey)
        ),
        toolRegistry = ToolRegistry.EMPTY,
        strategy = strategy<A2AMessage, Unit>("hello-agent") {

            // ── Node 1 : Setup — transformer le message A2A en prompt LLM ──
            val nodeSetup by node<A2AMessage, Unit> { inputMessage ->
                val input = inputMessage.toKoogMessage()
                llm.writeSession {
                    appendPrompt {
                        message(input)
                    }
                }

                // Créer la task dans le storage (premier event = Task, pas TaskStatusUpdateEvent)
                withA2AAgentServer {
                    createTask(inputMessage, TaskState.Submitted)
                }
            }

            // ── Node 2 : Appeler le LLM ──
            val nodeLLMRequest by node<Unit, Message> {
                llm.writeSession {
                    requestLLM()
                }
            }

            // ── Node 3 : Envoyer la réponse LLM comme Completed ──
            val nodeProcessAssistant by node<String, Unit> { assistantMessage ->
                withA2AAgentServer {
                    updateTaskStatus(assistantMessage, TaskState.Completed, final = true)
                }
            }

            // ── Edges ──
            edge(nodeStart forwardTo nodeSetup)
            edge(nodeSetup forwardTo nodeLLMRequest)
            edge(nodeLLMRequest forwardTo nodeProcessAssistant onTextMessage { true })
            edge(nodeProcessAssistant forwardTo nodeFinish)
        },
        agentConfig = AIAgentConfig(
            prompt = prompt("hello-agent") {
                system("""
                    Tu es un assistant amical et serviable qui s'appelle "HelloAgent".
                    Tu réponds de manière concise et en français.
                    Si on te demande qui tu es, explique que tu es un agent IA
                    exposé via le protocole A2A (Agent-to-Agent) et propulsé par Koog.
                """.trimIndent())
            },
            model = OpenRouterModels.GPT4o,
            maxAgentIterations = 5
        ),
    ) {
        install(A2AAgentServer) {
            this.context = context
            this.eventProcessor = eventProcessor
        }
    }
}

/**
 * Crée une nouvelle task dans le storage A2A.
 * Doit être appelée AVANT tout TaskStatusUpdateEvent.
 * Pattern calqué sur JokeWriterAgentExecutor.createTask.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
private suspend fun A2AAgentServer.createTask(
    userMessage: A2AMessage,
    state: TaskState,
) {
    val task = Task(
        id = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = state,
            message = userMessage,
            timestamp = KoogClock.System.now(),
        ),
    )
    eventProcessor.sendTaskEvent(task)
}

/**
 * Met à jour le status d'une task existante.
 * - final=false → event intermédiaire, le stream continue (Working)
 * - final=true  → event terminal, le stream se ferme (Completed, Failed)
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
private suspend fun A2AAgentServer.updateTaskStatus(
    content: String,
    state: TaskState,
    final: Boolean,
) {
    val taskStatusUpdate = TaskStatusUpdateEvent(
        taskId = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = state,
            message = A2AMessage(
                messageId = Uuid.random().toString(),
                role = Role.Agent,
                parts = listOf(TextPart(content)),
                taskId = context.taskId,
                contextId = context.contextId,
            ),
            timestamp = KoogClock.System.now(),
        ),
        final = final,
    )
    eventProcessor.sendTaskEvent(taskStatusUpdate)
}
