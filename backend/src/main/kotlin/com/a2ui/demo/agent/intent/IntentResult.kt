package com.a2ui.demo.agent.intent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ai.koog.agents.core.tools.annotations.LLMDescription

/**
 * Résultat structuré de l'analyse d'intention par le LLM.
 * Sérialisé en JSON pour la validation et la transmission au GeneratorAgent.
 */
@Serializable
@SerialName("IntentResult")
@LLMDescription("Structured intent analysis result for A2UI interface generation")
data class IntentResult(
    /** Le message original de l'utilisateur */
    @property:LLMDescription("The original user message, verbatim")
    val userMessage: String,
    /** Description concise de l'intention (1-2 phrases) */
    @property:LLMDescription("Concise, actionable description of what the user wants (1-2 sentences)")
    val intent: String,
    /** Justification de l'analyse d'intention */
    @property:LLMDescription("Justification explaining why this intent was identified")
    val justification: String,
    /** Type d'UI le plus adapté */
    @property:LLMDescription("The UI pattern type best suited for this intent")
    val uiType: UiType
)

@Serializable
@SerialName("UiType")
@LLMDescription("Available UI pattern types for A2UI interfaces")
enum class UiType {
    @SerialName("form") Form,
    @SerialName("card") Card,
    @SerialName("list") List,
    @SerialName("dashboard") Dashboard,
    @SerialName("info") Info,
    @SerialName("player") Player,
    @SerialName("notification") Notification,
    @SerialName("profile") Profile,
    @SerialName("compose") Compose,
    @SerialName("detail") Detail,
    @SerialName("status") Status,
    @SerialName("settings") Settings,
    @SerialName("login") Login,
    @SerialName("order") Order,
    @SerialName("booking") Booking
}
