package com.a2ui.demo.generator

import com.a2ui.demo.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val json = Json {
    encodeDefaults = false
    prettyPrint = false
}

/**
 * Génère le flux JSONL A2UI pour un formulaire de réservation de restaurant.
 * Ordre du protocole : createSurface → surfaceUpdate → updateDataModel → beginRendering
 */
class A2uiGenerator(private val surfaceId: String = "main") {

    fun generateReservationForm(): List<A2uiMessage> = listOf(
        buildCreateSurface(),
        buildSurfaceUpdate(),
        buildDataModel(),
        buildBeginRendering()
    )

    fun generateReservationFormJsonl(): String =
        generateReservationForm()
            .joinToString("\n") { json.encodeToString<A2uiMessage>(it) }

    private fun buildCreateSurface() = CreateSurface(surfaceId = surfaceId)

    private fun buildSurfaceUpdate() = SurfaceUpdate(
        surfaceId = surfaceId,
        components = listOf(
            // Racine : colonne principale
            ComponentEntry(
                id = "root",
                component = ColumnComponent(
                    children = ChildrenBinding(explicitList = listOf("header-card", "form-card", "submit-btn")),
                    spacing = 16
                )
            ),

            // Carte en-tête
            ComponentEntry(
                id = "header-card",
                component = CardComponent(child = "header-content")
            ),
            ComponentEntry(
                id = "header-content",
                component = ColumnComponent(
                    children = ChildrenBinding(explicitList = listOf("title-text", "subtitle-text"))
                )
            ),
            ComponentEntry(
                id = "title-text",
                component = TextComponent(
                    text = TextValue(path = "/restaurantName"),
                    style = "headline"
                )
            ),
            ComponentEntry(
                id = "subtitle-text",
                component = TextComponent(
                    text = TextValue(literalString = "Réservez votre table en quelques secondes"),
                    style = "body"
                )
            ),

            // Carte formulaire
            ComponentEntry(
                id = "form-card",
                component = CardComponent(child = "form-fields")
            ),
            ComponentEntry(
                id = "form-fields",
                component = ColumnComponent(
                    children = ChildrenBinding(
                        explicitList = listOf(
                            "field-name", "field-date", "field-time",
                            "field-guests", "field-occasion", "field-notes"
                        )
                    ),
                    spacing = 12
                )
            ),

            // Champ Nom
            ComponentEntry(
                id = "field-name",
                component = TextFieldComponent(
                    label = TextValue(literalString = "Nom complet"),
                    value = TextValue(path = "/guestName"),
                    placeholder = TextValue(literalString = "Jean Dupont"),
                    action = Action(name = "updateField", params = mapOf("field" to "guestName"))
                )
            ),

            // Champ Date
            ComponentEntry(
                id = "field-date",
                component = DateTimeInputComponent(
                    label = TextValue(literalString = "Date de réservation"),
                    value = TextValue(path = "/reservationDate"),
                    mode = "date",
                    action = Action(name = "updateField", params = mapOf("field" to "reservationDate"))
                )
            ),

            // Champ Heure
            ComponentEntry(
                id = "field-time",
                component = DateTimeInputComponent(
                    label = TextValue(literalString = "Heure"),
                    value = TextValue(path = "/reservationTime"),
                    mode = "time",
                    action = Action(name = "updateField", params = mapOf("field" to "reservationTime"))
                )
            ),

            // Champ Nombre de convives (Slider)
            ComponentEntry(
                id = "field-guests",
                component = SliderComponent(
                    min = 1.0,
                    max = 20.0,
                    step = 1.0,
                    value = TextValue(path = "/guestCount"),
                    action = Action(name = "updateField", params = mapOf("field" to "guestCount"))
                )
            ),

            // Champ Occasion (Dropdown)
            ComponentEntry(
                id = "field-occasion",
                component = DropdownComponent(
                    label = TextValue(literalString = "Occasion"),
                    value = TextValue(path = "/occasion"),
                    options = listOf(
                        ChoiceOption("none", TextValue(literalString = "Aucune occasion particulière")),
                        ChoiceOption("birthday", TextValue(literalString = "Anniversaire")),
                        ChoiceOption("anniversary", TextValue(literalString = "Anniversaire de mariage")),
                        ChoiceOption("business", TextValue(literalString = "Repas d'affaires")),
                        ChoiceOption("romantic", TextValue(literalString = "Dîner romantique"))
                    ),
                    action = Action(name = "updateField", params = mapOf("field" to "occasion"))
                )
            ),

            // Champ Notes (TextField multiligne)
            ComponentEntry(
                id = "field-notes",
                component = TextFieldComponent(
                    label = TextValue(literalString = "Demandes spéciales"),
                    value = TextValue(path = "/specialRequests"),
                    placeholder = TextValue(literalString = "Allergies, préférences de table, etc.")
                )
            ),

            // Bouton de soumission
            ComponentEntry(
                id = "submit-btn",
                component = ButtonComponent(
                    child = "submit-label",
                    action = Action(name = "submitReservation")
                )
            ),
            ComponentEntry(
                id = "submit-label",
                component = TextComponent(
                    text = TextValue(literalString = "Confirmer la réservation")
                )
            )
        )
    )

    private fun buildDataModel() = UpdateDataModel(
        surfaceId = surfaceId,
        contents = listOf(
            DataModelEntry(key = "restaurantName", valueString = "Le Bistrot Parisien"),
            DataModelEntry(key = "guestName", valueString = ""),
            DataModelEntry(key = "reservationDate", valueString = ""),
            DataModelEntry(key = "reservationTime", valueString = "19:30"),
            DataModelEntry(key = "guestCount", valueInt = 2),
            DataModelEntry(key = "occasion", valueString = "none"),
            DataModelEntry(key = "specialRequests", valueString = "")
        )
    )

    private fun buildBeginRendering() = BeginRendering(surfaceId = surfaceId, root = "root")
}
