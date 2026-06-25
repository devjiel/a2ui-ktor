package com.a2ui.demo.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

// ── Composants du catalogue Basic A2UI v0.9 ──────────────────────────────────

@Serializable(with = ComponentSerializer::class)
sealed class Component

// Layout
@Serializable data class ColumnComponent(val children: ChildrenBinding? = null, val spacing: Int? = null) : Component()
@Serializable data class RowComponent(val children: ChildrenBinding? = null, val spacing: Int? = null) : Component()
@Serializable data class CardComponent(val child: String? = null, val children: ChildrenBinding? = null, val title: TextValue? = null) : Component()
@Serializable data class ListComponent(val children: ChildrenBinding? = null, val dataSource: TextValue? = null) : Component()
@Serializable data class DividerComponent(val orientation: String? = null) : Component()
@Serializable data class ModalComponent(val child: String? = null, val open: TextValue? = null, val title: TextValue? = null) : Component()
@Serializable data class TabsComponent(val tabs: List<TabEntry>? = null) : Component()

// Text
@Serializable data class TextComponent(val text: TextValue? = null, val style: String? = null) : Component()
@Serializable data class MarkdownComponent(val text: TextValue? = null) : Component()

// Input
@Serializable data class TextFieldComponent(
    val label: TextValue? = null,
    val value: TextValue? = null,
    val placeholder: TextValue? = null,
    val action: Action? = null
) : Component()

@Serializable data class CheckBoxComponent(
    val label: TextValue? = null,
    val value: TextValue? = null,
    val action: Action? = null
) : Component()

@Serializable data class SliderComponent(
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val value: TextValue? = null,
    val action: Action? = null
) : Component()

@Serializable data class DateTimeInputComponent(
    val label: TextValue? = null,
    val value: TextValue? = null,
    val mode: String? = null,
    val action: Action? = null
) : Component()

@Serializable data class MultipleChoiceComponent(
    val label: TextValue? = null,
    val value: TextValue? = null,
    val options: List<ChoiceOption>? = null,
    val action: Action? = null
) : Component()

@Serializable data class DropdownComponent(
    val label: TextValue? = null,
    val value: TextValue? = null,
    val options: List<ChoiceOption>? = null,
    val action: Action? = null
) : Component()

// Média
@Serializable data class ImageComponent(val src: TextValue? = null, val alt: TextValue? = null, val width: Int? = null, val height: Int? = null) : Component()
@Serializable data class VideoComponent(val src: TextValue? = null, val autoPlay: Boolean? = null) : Component()
@Serializable data class AudioPlayerComponent(val src: TextValue? = null, val autoPlay: Boolean? = null) : Component()
@Serializable data class IconComponent(val name: TextValue? = null, val size: Int? = null) : Component()

// Action
@Serializable data class ButtonComponent(val child: String? = null, val action: Action? = null, val disabled: TextValue? = null) : Component()

// ── Entrée d'un composant dans le surfaceUpdate ───────────────────────────────

@Serializable
data class ComponentEntry(
    val id: String,
    val component: Component
)

// ── Serializer wrapper-key pour Component ─────────────────────────────────────

object ComponentSerializer : KSerializer<Component> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Component")

    override fun serialize(encoder: Encoder, value: Component) {
        require(encoder is JsonEncoder) { "Only JSON encoding is supported" }
        val json = encoder.json
        val (key, element) = when (value) {
            is ColumnComponent -> "Column" to json.encodeToJsonElement(ColumnComponent.serializer(), value)
            is RowComponent -> "Row" to json.encodeToJsonElement(RowComponent.serializer(), value)
            is CardComponent -> "Card" to json.encodeToJsonElement(CardComponent.serializer(), value)
            is ListComponent -> "List" to json.encodeToJsonElement(ListComponent.serializer(), value)
            is DividerComponent -> "Divider" to json.encodeToJsonElement(DividerComponent.serializer(), value)
            is ModalComponent -> "Modal" to json.encodeToJsonElement(ModalComponent.serializer(), value)
            is TabsComponent -> "Tabs" to json.encodeToJsonElement(TabsComponent.serializer(), value)
            is TextComponent -> "Text" to json.encodeToJsonElement(TextComponent.serializer(), value)
            is MarkdownComponent -> "Markdown" to json.encodeToJsonElement(MarkdownComponent.serializer(), value)
            is TextFieldComponent -> "TextField" to json.encodeToJsonElement(TextFieldComponent.serializer(), value)
            is CheckBoxComponent -> "CheckBox" to json.encodeToJsonElement(CheckBoxComponent.serializer(), value)
            is SliderComponent -> "Slider" to json.encodeToJsonElement(SliderComponent.serializer(), value)
            is DateTimeInputComponent -> "DateTimeInput" to json.encodeToJsonElement(DateTimeInputComponent.serializer(), value)
            is MultipleChoiceComponent -> "MultipleChoice" to json.encodeToJsonElement(MultipleChoiceComponent.serializer(), value)
            is DropdownComponent -> "Dropdown" to json.encodeToJsonElement(DropdownComponent.serializer(), value)
            is ImageComponent -> "Image" to json.encodeToJsonElement(ImageComponent.serializer(), value)
            is VideoComponent -> "Video" to json.encodeToJsonElement(VideoComponent.serializer(), value)
            is AudioPlayerComponent -> "AudioPlayer" to json.encodeToJsonElement(AudioPlayerComponent.serializer(), value)
            is IconComponent -> "Icon" to json.encodeToJsonElement(IconComponent.serializer(), value)
            is ButtonComponent -> "Button" to json.encodeToJsonElement(ButtonComponent.serializer(), value)
        }
        encoder.encodeJsonElement(buildJsonObject { put(key, element) })
    }

    override fun deserialize(decoder: Decoder): Component {
        require(decoder is JsonDecoder) { "Only JSON decoding is supported" }
        val obj = decoder.decodeJsonElement().jsonObject
        val json = decoder.json
        return when {
            "Column" in obj -> json.decodeFromJsonElement(ColumnComponent.serializer(), obj["Column"]!!)
            "Row" in obj -> json.decodeFromJsonElement(RowComponent.serializer(), obj["Row"]!!)
            "Card" in obj -> json.decodeFromJsonElement(CardComponent.serializer(), obj["Card"]!!)
            "List" in obj -> json.decodeFromJsonElement(ListComponent.serializer(), obj["List"]!!)
            "Divider" in obj -> json.decodeFromJsonElement(DividerComponent.serializer(), obj["Divider"]!!)
            "Modal" in obj -> json.decodeFromJsonElement(ModalComponent.serializer(), obj["Modal"]!!)
            "Tabs" in obj -> json.decodeFromJsonElement(TabsComponent.serializer(), obj["Tabs"]!!)
            "Text" in obj -> json.decodeFromJsonElement(TextComponent.serializer(), obj["Text"]!!)
            "Markdown" in obj -> json.decodeFromJsonElement(MarkdownComponent.serializer(), obj["Markdown"]!!)
            "TextField" in obj -> json.decodeFromJsonElement(TextFieldComponent.serializer(), obj["TextField"]!!)
            "CheckBox" in obj -> json.decodeFromJsonElement(CheckBoxComponent.serializer(), obj["CheckBox"]!!)
            "Slider" in obj -> json.decodeFromJsonElement(SliderComponent.serializer(), obj["Slider"]!!)
            "DateTimeInput" in obj -> json.decodeFromJsonElement(DateTimeInputComponent.serializer(), obj["DateTimeInput"]!!)
            "MultipleChoice" in obj -> json.decodeFromJsonElement(MultipleChoiceComponent.serializer(), obj["MultipleChoice"]!!)
            "Dropdown" in obj -> json.decodeFromJsonElement(DropdownComponent.serializer(), obj["Dropdown"]!!)
            "Image" in obj -> json.decodeFromJsonElement(ImageComponent.serializer(), obj["Image"]!!)
            "Video" in obj -> json.decodeFromJsonElement(VideoComponent.serializer(), obj["Video"]!!)
            "AudioPlayer" in obj -> json.decodeFromJsonElement(AudioPlayerComponent.serializer(), obj["AudioPlayer"]!!)
            "Icon" in obj -> json.decodeFromJsonElement(IconComponent.serializer(), obj["Icon"]!!)
            "Button" in obj -> json.decodeFromJsonElement(ButtonComponent.serializer(), obj["Button"]!!)
            else -> throw SerializationException("Unknown component type: ${obj.keys}")
        }
    }
}
