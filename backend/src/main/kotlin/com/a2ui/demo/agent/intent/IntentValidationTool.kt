package com.a2ui.demo.agent.intent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.json.Json

/**
 * Tool de validation déterministe pour la sortie JSON de l'IntentAgent.
 * Vérifie 5 règles : JSON valide, champs requis non vides, uiType reconnu.
 *
 * Retourne un JSON: {"valid": true/false, "errors": ["...", "..."]}
 */
@LLMDescription("Tools for validating intent analysis output")
class IntentValidationTools : ToolSet {

    private val json = Json { ignoreUnknownKeys = true }

    @Tool
    @LLMDescription("Validates the structured JSON output of the intent analysis. Returns validation result with errors if any.")
    fun validate_intent(
        @LLMDescription("The JSON string to validate")
        intentJson: String
    ): String {
        val errors = mutableListOf<String>()

        // Règle 1 : JSON valide et conforme au schéma IntentResult
        val parsed = try {
            json.decodeFromString<IntentResult>(intentJson)
        } catch (e: Exception) {
            errors.add("JSON invalide ou ne correspond pas au schéma IntentResult: ${e.message}")
            return formatResult(false, errors)
        }

        // Règle 2 : userMessage non vide
        if (parsed.userMessage.isBlank()) {
            errors.add("Le champ 'userMessage' est vide")
        }

        // Règle 3 : intent non vide
        if (parsed.intent.isBlank()) {
            errors.add("Le champ 'intent' est vide")
        }

        // Règle 4 : justification non vide
        if (parsed.justification.isBlank()) {
            errors.add("Le champ 'justification' est vide")
        }

        // Règle 5 : uiType valide
        if (parsed.uiType.isBlank()) {
            errors.add("Le champ 'uiType' est vide")
        } else if (parsed.uiType !in IntentResult.VALID_UI_TYPES) {
            errors.add("uiType '${parsed.uiType}' n'est pas reconnu. Types valides: ${IntentResult.VALID_UI_TYPES.joinToString()}")
        }

        val valid = errors.isEmpty()
        return formatResult(valid, errors)
    }

    private fun formatResult(valid: Boolean, errors: List<String>): String {
        return """{"valid": $valid, "errors": ${Json.encodeToString(errors)}}"""
    }
}
