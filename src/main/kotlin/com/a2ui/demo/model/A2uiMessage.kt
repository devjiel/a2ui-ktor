package com.a2ui.demo.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

// ── Messages A2UI v0.9 ────────────────────────────────────────────────────────

@Serializable(with = A2uiMessageSerializer::class)
sealed class A2uiMessage

/** Crée une surface de rendu avec son catalogue de composants. */
@Serializable
data class CreateSurface(
    val surfaceId: String,
    val catalogId: String = "https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json"
) : A2uiMessage()

/** Définit la structure des composants d'une surface. */
@Serializable
data class SurfaceUpdate(
    val surfaceId: String,
    val components: List<ComponentEntry>
) : A2uiMessage()

/** Met à jour le modèle de données lié aux composants. */
@Serializable
data class UpdateDataModel(
    val surfaceId: String,
    val contents: List<DataModelEntry>
) : A2uiMessage()

/** Active le rendu de la surface en désignant le composant racine. */
@Serializable
data class BeginRendering(
    val surfaceId: String,
    val root: String
) : A2uiMessage()

/** Supprime une surface et libère ses ressources. */
@Serializable
data class DeleteSurface(
    val surfaceId: String
) : A2uiMessage()

// ── Serializer wrapper-key pour A2uiMessage ───────────────────────────────────

object A2uiMessageSerializer : KSerializer<A2uiMessage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("A2uiMessage")

    override fun serialize(encoder: Encoder, value: A2uiMessage) {
        require(encoder is JsonEncoder) { "Only JSON encoding is supported" }
        val json = encoder.json
        val (key, element) = when (value) {
            is CreateSurface -> "createSurface" to json.encodeToJsonElement(CreateSurface.serializer(), value)
            is SurfaceUpdate -> "surfaceUpdate" to json.encodeToJsonElement(SurfaceUpdate.serializer(), value)
            is UpdateDataModel -> "updateDataModel" to json.encodeToJsonElement(UpdateDataModel.serializer(), value)
            is BeginRendering -> "beginRendering" to json.encodeToJsonElement(BeginRendering.serializer(), value)
            is DeleteSurface -> "deleteSurface" to json.encodeToJsonElement(DeleteSurface.serializer(), value)
        }
        encoder.encodeJsonElement(buildJsonObject { put(key, element) })
    }

    override fun deserialize(decoder: Decoder): A2uiMessage {
        require(decoder is JsonDecoder) { "Only JSON decoding is supported" }
        val obj = decoder.decodeJsonElement().jsonObject
        val json = decoder.json
        return when {
            "createSurface" in obj -> json.decodeFromJsonElement(CreateSurface.serializer(), obj["createSurface"]!!)
            "surfaceUpdate" in obj -> json.decodeFromJsonElement(SurfaceUpdate.serializer(), obj["surfaceUpdate"]!!)
            "updateDataModel" in obj -> json.decodeFromJsonElement(UpdateDataModel.serializer(), obj["updateDataModel"]!!)
            "beginRendering" in obj -> json.decodeFromJsonElement(BeginRendering.serializer(), obj["beginRendering"]!!)
            "deleteSurface" in obj -> json.decodeFromJsonElement(DeleteSurface.serializer(), obj["deleteSurface"]!!)
            else -> throw SerializationException("Unknown A2UI message type: ${obj.keys}")
        }
    }
}
