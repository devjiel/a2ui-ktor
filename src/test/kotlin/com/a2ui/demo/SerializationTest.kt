package com.a2ui.demo

import com.a2ui.demo.model.*
import com.a2ui.demo.generator.json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SerializationTest {

    // ── Messages A2UI ──────────────────────────────────────────────────────────

    @Test
    fun `CreateSurface serialise avec la bonne clé wrapper`() {
        val msg = CreateSurface(surfaceId = "main")
        val jsonStr = json.encodeToString<A2uiMessage>(msg)
        assertTrue(jsonStr.contains("\"createSurface\""), "Doit contenir la clé 'createSurface'")
        assertTrue(jsonStr.contains("\"surfaceId\""), "Doit contenir surfaceId")
        assertTrue(jsonStr.contains("\"main\""), "Doit contenir la valeur 'main'")
    }

    @Test
    fun `CreateSurface round-trip`() {
        val original = CreateSurface(surfaceId = "form", catalogId = "https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json")
        val encoded = json.encodeToString<A2uiMessage>(original)
        val decoded = json.decodeFromString<A2uiMessage>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `SurfaceUpdate serialise correctement`() {
        val msg = SurfaceUpdate(
            surfaceId = "main",
            components = listOf(
                ComponentEntry(id = "root", component = ColumnComponent(
                    children = ChildrenBinding(explicitList = listOf("header", "body"))
                ))
            )
        )
        val jsonStr = json.encodeToString<A2uiMessage>(msg)
        assertTrue(jsonStr.contains("\"surfaceUpdate\""))
        assertTrue(jsonStr.contains("\"Column\""))
        assertTrue(jsonStr.contains("\"explicitList\""))
    }

    @Test
    fun `SurfaceUpdate round-trip`() {
        val original = SurfaceUpdate(
            surfaceId = "main",
            components = listOf(
                ComponentEntry("root", ColumnComponent(ChildrenBinding(listOf("a", "b")))),
                ComponentEntry("a", TextComponent(TextValue(literalString = "Hello"))),
                ComponentEntry("b", ButtonComponent(child = "lbl", action = Action("click")))
            )
        )
        val encoded = json.encodeToString<A2uiMessage>(original)
        val decoded = json.decodeFromString<A2uiMessage>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `UpdateDataModel serialise correctement`() {
        val msg = UpdateDataModel(
            surfaceId = "main",
            contents = listOf(
                DataModelEntry(key = "title", valueString = "Bonjour"),
                DataModelEntry(key = "count", valueInt = 3),
                DataModelEntry(key = "active", valueBool = true)
            )
        )
        val jsonStr = json.encodeToString<A2uiMessage>(msg)
        assertTrue(jsonStr.contains("\"updateDataModel\""))
        assertTrue(jsonStr.contains("\"Bonjour\""))
        assertTrue(jsonStr.contains("\"valueInt\""))
    }

    @Test
    fun `UpdateDataModel round-trip`() {
        val original = UpdateDataModel(
            surfaceId = "main",
            contents = listOf(
                DataModelEntry(key = "name", valueString = "Alice"),
                DataModelEntry(key = "age", valueInt = 30),
                DataModelEntry(key = "active", valueBool = false)
            )
        )
        val encoded = json.encodeToString<A2uiMessage>(original)
        val decoded = json.decodeFromString<A2uiMessage>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `BeginRendering serialise correctement`() {
        val msg = BeginRendering(surfaceId = "main", root = "root")
        val jsonStr = json.encodeToString<A2uiMessage>(msg)
        assertTrue(jsonStr.contains("\"beginRendering\""))
        assertTrue(jsonStr.contains("\"root\""))
    }

    @Test
    fun `BeginRendering round-trip`() {
        val original = BeginRendering(surfaceId = "main", root = "root-container")
        val encoded = json.encodeToString<A2uiMessage>(original)
        val decoded = json.decodeFromString<A2uiMessage>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `DeleteSurface round-trip`() {
        val original = DeleteSurface(surfaceId = "main")
        val encoded = json.encodeToString<A2uiMessage>(original)
        val decoded = json.decodeFromString<A2uiMessage>(encoded)
        assertEquals(original, decoded)
    }

    // ── Composants ─────────────────────────────────────────────────────────────

    @Test
    fun `TextComponent avec path serialise correctement`() {
        val entry = ComponentEntry("lbl", TextComponent(text = TextValue(path = "/title")))
        val encoded = json.encodeToString(ComponentEntry.serializer(), entry)
        assertTrue(encoded.contains("\"Text\""))
        assertTrue(encoded.contains("\"path\""))
        assertTrue(encoded.contains("\"/title\""))
    }

    @Test
    fun `TextComponent avec literalString serialise correctement`() {
        val entry = ComponentEntry("lbl", TextComponent(text = TextValue(literalString = "Submit")))
        val encoded = json.encodeToString(ComponentEntry.serializer(), entry)
        assertTrue(encoded.contains("\"literalString\""))
        assertTrue(encoded.contains("\"Submit\""))
    }

    @Test
    fun `ButtonComponent round-trip`() {
        val original = ComponentEntry(
            "btn",
            ButtonComponent(child = "btn-lbl", action = Action("submit", mapOf("form" to "reservation")))
        )
        val encoded = json.encodeToString(ComponentEntry.serializer(), original)
        val decoded = json.decodeFromString(ComponentEntry.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `DropdownComponent round-trip`() {
        val original = ComponentEntry(
            "dd",
            DropdownComponent(
                label = TextValue(literalString = "Occasion"),
                value = TextValue(path = "/occasion"),
                options = listOf(
                    ChoiceOption("birthday", TextValue(literalString = "Anniversaire")),
                    ChoiceOption("none", TextValue(literalString = "Aucune"))
                ),
                action = Action("selectOccasion")
            )
        )
        val encoded = json.encodeToString(ComponentEntry.serializer(), original)
        val decoded = json.decodeFromString(ComponentEntry.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `SliderComponent round-trip`() {
        val original = ComponentEntry(
            "slider",
            SliderComponent(min = 1.0, max = 20.0, step = 1.0, value = TextValue(path = "/guests"))
        )
        val encoded = json.encodeToString(ComponentEntry.serializer(), original)
        val decoded = json.decodeFromString(ComponentEntry.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `tous les types de composants se serialisent sans erreur`() {
        val components = listOf(
            ComponentEntry("col", ColumnComponent(ChildrenBinding(listOf("a")))),
            ComponentEntry("row", RowComponent(ChildrenBinding(listOf("a")))),
            ComponentEntry("card", CardComponent(child = "c")),
            ComponentEntry("list", ListComponent(ChildrenBinding(listOf("a")))),
            ComponentEntry("div", DividerComponent()),
            ComponentEntry("modal", ModalComponent(child = "c")),
            ComponentEntry("tabs", TabsComponent(listOf(TabEntry(TextValue(literalString = "T1"), "c1")))),
            ComponentEntry("txt", TextComponent(TextValue(literalString = "hello"))),
            ComponentEntry("md", MarkdownComponent(TextValue(literalString = "**bold**"))),
            ComponentEntry("tf", TextFieldComponent(label = TextValue(literalString = "Name"))),
            ComponentEntry("cb", CheckBoxComponent(label = TextValue(literalString = "Accept"))),
            ComponentEntry("sl", SliderComponent(min = 0.0, max = 100.0)),
            ComponentEntry("dt", DateTimeInputComponent(mode = "date")),
            ComponentEntry("mc", MultipleChoiceComponent(label = TextValue(literalString = "Pick"))),
            ComponentEntry("dd", DropdownComponent(label = TextValue(literalString = "Select"))),
            ComponentEntry("img", ImageComponent(src = TextValue(literalString = "https://example.com/img.png"))),
            ComponentEntry("vid", VideoComponent(src = TextValue(literalString = "https://example.com/video.mp4"))),
            ComponentEntry("audio", AudioPlayerComponent(src = TextValue(literalString = "https://example.com/audio.mp3"))),
            ComponentEntry("icon", IconComponent(name = TextValue(literalString = "star"))),
            ComponentEntry("btn", ButtonComponent(child = "lbl", action = Action("click")))
        )

        for (entry in components) {
            val encoded = json.encodeToString(ComponentEntry.serializer(), entry)
            assertNotNull(encoded, "Encodage null pour ${entry.id}")
            assertTrue(encoded.isNotBlank(), "Encodage vide pour ${entry.id}")
        }
    }

    // ── Format JSON A2UI spec ──────────────────────────────────────────────────

    @Test
    fun `le JSON produit correspond au format spec A2UI`() {
        val msg = UpdateDataModel(
            surfaceId = "main",
            contents = listOf(DataModelEntry(key = "title", valueString = "Hello A2UI"))
        )
        val expected = """{"updateDataModel":{"surfaceId":"main","contents":[{"key":"title","valueString":"Hello A2UI"}]}}"""
        val actual = json.encodeToString<A2uiMessage>(msg)
        assertEquals(expected, actual)
    }

    @Test
    fun `un surfaceUpdate avec Column correspond au format spec`() {
        val msg = SurfaceUpdate(
            surfaceId = "main",
            components = listOf(
                ComponentEntry(
                    id = "root",
                    component = ColumnComponent(children = ChildrenBinding(explicitList = listOf("header", "body")))
                )
            )
        )
        val jsonStr = json.encodeToString<A2uiMessage>(msg)
        // Vérifie la structure wrapper-key
        assertTrue(jsonStr.startsWith("{\"surfaceUpdate\":"))
        assertTrue(jsonStr.contains("\"Column\":{\"children\":{\"explicitList\""))
    }
}
