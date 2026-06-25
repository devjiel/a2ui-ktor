package com.a2ui.demo.model

import kotlinx.serialization.Serializable

/**
 * Référence une valeur via un chemin JSON Pointer ou une valeur littérale.
 * Ex: {"path": "/title"} ou {"literalString": "Submit"}
 */
@Serializable
data class TextValue(
    val path: String? = null,
    val literalString: String? = null,
    val literalInt: Int? = null,
    val literalBool: Boolean? = null
)

/** Binding pour les enfants d'un composant layout. */
@Serializable
data class ChildrenBinding(
    val explicitList: List<String>? = null
)

/** Action utilisateur attachée à un composant interactif. */
@Serializable
data class Action(
    val name: String,
    val params: Map<String, String>? = null
)

/** Onglet pour le composant Tabs. */
@Serializable
data class TabEntry(
    val label: TextValue,
    val child: String
)

/** Option pour les composants MultipleChoice/Dropdown. */
@Serializable
data class ChoiceOption(
    val value: String,
    val label: TextValue
)

/** Entrée dans le dataModel — une valeur typée liée à une clé. */
@Serializable
data class DataModelEntry(
    val key: String,
    val valueString: String? = null,
    val valueInt: Int? = null,
    val valueBool: Boolean? = null,
    val valueMap: Map<String, String>? = null,
    val valueList: List<String>? = null
)
