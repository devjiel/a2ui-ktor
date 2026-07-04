@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.a2ui.demo.agent

import ai.koog.a2a.model.*
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.agents.a2a.core.A2AMessage
import ai.koog.agents.a2a.server.feature.A2AAgentServer
import ai.koog.utils.time.KoogClock
import kotlin.uuid.Uuid

// ──────────────────────────────────────────────────────────────
// Core standalone functions (context + eventProcessor as params)
// Usable anywhere — inside or outside an AIAgent.
// ──────────────────────────────────────────────────────────────

/**
 * Crée une nouvelle task dans le storage A2A.
 * Doit être appelée AVANT tout [TaskStatusUpdateEvent].
 */
internal suspend fun sendTaskCreated(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor,
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
internal suspend fun sendStatusUpdate(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor,
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

/**
 * Met à jour le status d'une task avec des parts custom (ex: DataPart A2UI).
 * Utilisé pour transmettre des DataPart(application/a2ui+json) au client.
 */
internal suspend fun sendStatusUpdateWithParts(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor,
    parts: List<Part>,
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
                parts = parts,
                taskId = context.taskId,
                contextId = context.contextId,
            ),
            timestamp = KoogClock.System.now(),
        ),
        final = final,
    )
    eventProcessor.sendTaskEvent(taskStatusUpdate)
}

// ──────────────────────────────────────────────────────────────
// A2AAgentServer extensions (delegate to standalone functions)
// For use inside AIAgent nodes via withA2AAgentServer { ... }
// ──────────────────────────────────────────────────────────────

/** @see sendTaskCreated */
internal suspend fun A2AAgentServer.createTask(
    userMessage: A2AMessage,
    state: TaskState,
) = sendTaskCreated(context, eventProcessor, userMessage, state)

/** @see sendStatusUpdate */
internal suspend fun A2AAgentServer.updateTaskStatus(
    content: String,
    state: TaskState,
    final: Boolean,
) = sendStatusUpdate(context, eventProcessor, content, state, final)

/** @see sendStatusUpdateWithParts */
internal suspend fun A2AAgentServer.updateTaskStatusWithParts(
    parts: List<Part>,
    state: TaskState,
    final: Boolean,
) = sendStatusUpdateWithParts(context, eventProcessor, parts, state, final)
