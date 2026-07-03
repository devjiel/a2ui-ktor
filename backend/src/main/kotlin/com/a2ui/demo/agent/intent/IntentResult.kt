package com.a2ui.demo.agent.intent

import kotlinx.serialization.Serializable

/**
 * Résultat structuré de l'analyse d'intention par le LLM.
 * Sérialisé en JSON pour la validation et la transmission au GeneratorAgent.
 */
@Serializable
data class IntentResult(
    /** Le message original de l'utilisateur */
    val userMessage: String,
    /** Description concise de l'intention (1-2 phrases) */
    val intent: String,
    /** Justification de l'analyse d'intention */
    val justification: String,
    /** Type d'UI le plus adapté */
    val uiType: String
) {
    companion object {
        /** Types d'UI reconnus par le système */
        val VALID_UI_TYPES = setOf(
            "form", "card", "list", "dashboard", "info", "player",
            "notification", "profile", "compose", "detail", "status",
            "settings", "login", "order", "booking"
        )
    }
}
