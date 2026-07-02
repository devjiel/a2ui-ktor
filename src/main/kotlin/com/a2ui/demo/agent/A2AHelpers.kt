@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.a2ui.demo.agent

import ai.koog.a2a.model.*
import ai.koog.agents.a2a.core.A2AMessage
import ai.koog.agents.a2a.server.feature.A2AAgentServer
import ai.koog.utils.time.KoogClock
import kotlin.uuid.Uuid

/**
 * Crée une nouvelle task dans le storage A2A.
 * Doit être appelée AVANT tout [TaskStatusUpdateEvent].
 */
internal suspend fun A2AAgentServer.createTask(
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
internal suspend fun A2AAgentServer.updateTaskStatus(
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
