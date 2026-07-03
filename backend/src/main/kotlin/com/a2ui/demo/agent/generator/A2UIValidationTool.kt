package com.a2ui.demo.agent.generator

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.json.*

/**
 * Validation déterministe (grounded verification) du JSON A2UI généré.
 * 12 règles codées en dur, aucun appel LLM.
 */
@LLMDescription("Tools for validating A2UI JSON messages")
class A2UIValidationTools : ToolSet {

    /** Les 18 composants du catalog basic A2UI */
    private val VALID_COMPONENTS = setOf(
        "Text", "Image", "Icon", "Video", "AudioPlayer",
        "Row", "Column", "List", "Card", "Tabs", "Modal", "Divider",
        "Button", "TextField", "CheckBox", "ChoicePicker", "Slider", "DateTimeInput"
    )

    /** Propriétés required par composant (au-delà de "id" et "component") */
    private val REQUIRED_PROPS = mapOf(
        "Text" to setOf("text"),
        "Image" to setOf("url"),
        "Icon" to setOf("name"),
        "Video" to setOf("url"),
        "AudioPlayer" to setOf("url"),
        "Row" to setOf("children"),
        "Column" to setOf("children"),
        "List" to setOf("children"),
        "Card" to setOf("child"),
        "Tabs" to setOf("tabs"),
        "Modal" to setOf("trigger", "content"),
        "Divider" to emptySet(),
        "Button" to setOf("child", "action"),
        "TextField" to setOf("label"),
        "CheckBox" to setOf("label", "value"),
        "ChoicePicker" to setOf("options", "value"),
        "Slider" to setOf("value", "max"),
        "DateTimeInput" to setOf("value")
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Tool
    @LLMDescription("Validates A2UI JSON messages against 12 deterministic rules. Returns validation result with list of errors.")
    fun validate_a2ui(
        @LLMDescription("The A2UI JSON string containing a messages array to validate")
        a2uiJson: String
    ): String {
        val errors = mutableListOf<String>()

        // ── Règle 1 : JSON valide ──
        val root: JsonObject
        try {
            root = json.parseToJsonElement(a2uiJson).jsonObject
        } catch (e: Exception) {
            errors.add("R1: JSON invalide: ${e.message}")
            return formatResult(false, errors)
        }

        // Extraire le tableau de messages
        val messages: JsonArray
        try {
            messages = root["messages"]?.jsonArray
                ?: throw IllegalArgumentException("Clé 'messages' manquante ou pas un array")
        } catch (e: Exception) {
            errors.add("R1: ${e.message}")
            return formatResult(false, errors)
        }

        // ── Règle 2 : Messages requis (createSurface + updateComponents) ──
        val hasCreateSurface = messages.any { it.jsonObject.containsKey("createSurface") }
        val hasUpdateComponents = messages.any { it.jsonObject.containsKey("updateComponents") }
        if (!hasCreateSurface) errors.add("R2: Message 'createSurface' manquant")
        if (!hasUpdateComponents) errors.add("R2: Message 'updateComponents' manquant")

        // ── Règle 3 : Version "v0.9" sur chaque message ──
        messages.forEachIndexed { i, msg ->
            val version = msg.jsonObject["version"]?.jsonPrimitive?.contentOrNull
            if (version != "v0.9" && version != "v0.9.1") {
                errors.add("R3: Message[$i] a version='$version' au lieu de 'v0.9'")
            }
        }

        // ── Collecter les composants ──
        val allComponents = mutableListOf<JsonObject>()
        val allIds = mutableSetOf<String>()
        var surfaceId: String? = null

        messages.forEach { msg ->
            val obj = msg.jsonObject
            obj["createSurface"]?.jsonObject?.let { cs ->
                surfaceId = cs["surfaceId"]?.jsonPrimitive?.contentOrNull
            }
            obj["updateComponents"]?.jsonObject?.let { uc ->
                uc["components"]?.jsonArray?.forEach { comp ->
                    allComponents.add(comp.jsonObject)
                    val id = comp.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    if (id != null) allIds.add(id)
                }
            }
        }

        // ── Règle 4 : surfaceId cohérent ──
        if (surfaceId != null) {
            messages.forEach { msg ->
                val obj = msg.jsonObject
                listOf("updateComponents", "updateDataModel", "deleteSurface").forEach { key ->
                    obj[key]?.jsonObject?.let { inner ->
                        val sid = inner["surfaceId"]?.jsonPrimitive?.contentOrNull
                        if (sid != null && sid != surfaceId) {
                            errors.add("R4: surfaceId incohérent: '$sid' vs '$surfaceId'")
                        }
                    }
                }
            }
        }

        // ── Vérifications par composant ──
        val componentIds = mutableSetOf<String>()

        allComponents.forEach { comp ->
            val id = comp["id"]?.jsonPrimitive?.contentOrNull ?: ""
            val componentType = comp["component"]?.jsonPrimitive?.contentOrNull ?: ""

            // ── Règle 5 : Composant valide ──
            if (componentType !in VALID_COMPONENTS) {
                errors.add("R5: Composant '$componentType' (id='$id') n'est pas dans le catalog")
            }
            if (id.isBlank()) {
                errors.add("R5: Un composant a un 'id' vide")
            }
            if (id in componentIds) {
                errors.add("R5: ID dupliqué: '$id'")
            }
            componentIds.add(id)

            // ── Règle 6 : Required properties ──
            val required = REQUIRED_PROPS[componentType] ?: emptySet()
            required.forEach { prop ->
                if (!comp.containsKey(prop)) {
                    errors.add("R6: Composant '$id' ($componentType) manque la propriété required '$prop'")
                }
            }

            // ── Règle 7 : Références d'IDs ──
            comp["child"]?.jsonPrimitive?.contentOrNull?.let { childId ->
                if (childId !in allIds) {
                    errors.add("R7: Composant '$id' référence child='$childId' qui n'existe pas")
                }
            }
            comp["children"]?.let { children ->
                when (children) {
                    is JsonArray -> {
                        children.forEach { childEl ->
                            val childId = childEl.jsonPrimitive.contentOrNull
                            if (childId != null && childId !in allIds) {
                                errors.add("R7: Composant '$id' référence children='$childId' qui n'existe pas")
                            }
                        }
                    }
                    is JsonObject -> {
                        val templateId = children["componentId"]?.jsonPrimitive?.contentOrNull
                        if (templateId != null && templateId !in allIds) {
                            errors.add("R7: Composant '$id' template référence componentId='$templateId' qui n'existe pas")
                        }
                    }
                    else -> {}
                }
            }
            listOf("trigger", "content").forEach { prop ->
                comp[prop]?.jsonPrimitive?.contentOrNull?.let { refId ->
                    if (refId !in allIds) {
                        errors.add("R7: Composant '$id' référence $prop='$refId' qui n'existe pas")
                    }
                }
            }
        }

        // ── Règle 9 : Root exists ──
        if ("root" !in componentIds && allComponents.isNotEmpty()) {
            errors.add("R9: Aucun composant avec id='root' trouvé")
        }

        // ── Règle 10 : Actions valides ──
        allComponents.forEach { comp ->
            val id = comp["id"]?.jsonPrimitive?.contentOrNull ?: ""
            comp["action"]?.jsonObject?.let { action ->
                val hasEvent = action.containsKey("event")
                val hasFunctionCall = action.containsKey("functionCall")
                if (hasEvent && hasFunctionCall) {
                    errors.add("R10: Composant '$id' a event ET functionCall (un seul autorisé)")
                }
                if (!hasEvent && !hasFunctionCall) {
                    errors.add("R10: Composant '$id' a une action sans event ni functionCall")
                }
                // Validate event structure
                if (hasEvent) {
                    val event = action["event"]?.jsonObject
                    if (event != null && !event.containsKey("name")) {
                        errors.add("R10: Composant '$id' event manque 'name'")
                    }
                }
                // Validate functionCall structure: must have "name" and "arguments"
                if (hasFunctionCall) {
                    val fc = action["functionCall"]?.jsonObject
                    if (fc != null) {
                        if (!fc.containsKey("name")) {
                            errors.add("R10: Composant '$id' functionCall manque 'name'")
                        }
                        // Warn if using old format "call" instead of "name"
                        if (fc.containsKey("call")) {
                            errors.add("R10: Composant '$id' functionCall utilise 'call' au lieu de 'name'")
                        }
                    }
                }
            }
        }

        // ── Règle 11 : ChildList format ──
        allComponents.forEach { comp ->
            val id = comp["id"]?.jsonPrimitive?.contentOrNull ?: ""
            comp["children"]?.let { children ->
                if (children !is JsonArray && children !is JsonObject) {
                    errors.add("R11: Composant '$id' a 'children' qui n'est ni un array ni un objet")
                }
            }
        }

        // ── Règle 12 : DataModel cohérence ──
        val referencedPaths = mutableSetOf<String>()
        allComponents.forEach { comp ->
            fun findPaths(element: JsonElement) {
                when (element) {
                    is JsonObject -> {
                        element["path"]?.jsonPrimitive?.contentOrNull?.let { path ->
                            if (path.startsWith("/")) referencedPaths.add(path)
                        }
                        element.values.forEach { findPaths(it) }
                    }
                    is JsonArray -> element.forEach { findPaths(it) }
                    else -> {}
                }
            }
            findPaths(comp)
        }

        if (referencedPaths.isNotEmpty()) {
            val hasUpdateDataModel = messages.any { it.jsonObject.containsKey("updateDataModel") }
            if (!hasUpdateDataModel) {
                errors.add("R12: Des composants utilisent du data binding (${referencedPaths.take(3).joinToString()}) mais aucun updateDataModel n'est présent")
            }
        }

        val valid = errors.isEmpty()
        return formatResult(valid, errors)
    }

    private fun formatResult(valid: Boolean, errors: List<String>): String {
        return """{"valid": $valid, "errors": ${Json.encodeToString(errors)}}"""
    }
}
