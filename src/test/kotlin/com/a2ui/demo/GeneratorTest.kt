package com.a2ui.demo

import com.a2ui.demo.generator.A2uiGenerator
import com.a2ui.demo.generator.json
import com.a2ui.demo.model.*
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GeneratorTest {

    private val generator = A2uiGenerator("main")

    @Test
    fun `generateReservationForm produit exactement 4 messages`() {
        val messages = generator.generateReservationForm()
        assertEquals(4, messages.size, "Doit produire 4 messages : createSurface, surfaceUpdate, updateDataModel, beginRendering")
    }

    @Test
    fun `premier message est CreateSurface`() {
        val messages = generator.generateReservationForm()
        assertInstanceOf(CreateSurface::class.java, messages[0])
        val msg = messages[0] as CreateSurface
        assertEquals("main", msg.surfaceId)
        assertTrue(msg.catalogId.contains("a2ui.org"), "catalogId doit pointer vers le catalogue A2UI")
    }

    @Test
    fun `deuxieme message est SurfaceUpdate avec des composants`() {
        val messages = generator.generateReservationForm()
        assertInstanceOf(SurfaceUpdate::class.java, messages[1])
        val msg = messages[1] as SurfaceUpdate
        assertEquals("main", msg.surfaceId)
        assertTrue(msg.components.isNotEmpty(), "SurfaceUpdate doit contenir des composants")
    }

    @Test
    fun `SurfaceUpdate contient un composant root Column`() {
        val messages = generator.generateReservationForm()
        val surfaceUpdate = messages[1] as SurfaceUpdate
        val rootEntry = surfaceUpdate.components.find { it.id == "root" }
        assertNotNull(rootEntry, "Doit contenir un composant avec id='root'")
        assertInstanceOf(ColumnComponent::class.java, rootEntry!!.component)
    }

    @Test
    fun `SurfaceUpdate contient les champs du formulaire de reservation`() {
        val messages = generator.generateReservationForm()
        val surfaceUpdate = messages[1] as SurfaceUpdate
        val ids = surfaceUpdate.components.map { it.id }.toSet()

        assertTrue("field-name" in ids, "Doit avoir un champ Nom")
        assertTrue("field-date" in ids, "Doit avoir un champ Date")
        assertTrue("field-time" in ids, "Doit avoir un champ Heure")
        assertTrue("field-guests" in ids, "Doit avoir un champ Nombre de convives")
        assertTrue("field-occasion" in ids, "Doit avoir un champ Occasion")
        assertTrue("submit-btn" in ids, "Doit avoir un bouton de soumission")
    }

    @Test
    fun `SurfaceUpdate contient un Dropdown pour l'occasion`() {
        val messages = generator.generateReservationForm()
        val surfaceUpdate = messages[1] as SurfaceUpdate
        val occasionField = surfaceUpdate.components.find { it.id == "field-occasion" }
        assertNotNull(occasionField)
        assertInstanceOf(DropdownComponent::class.java, occasionField!!.component)
        val dropdown = occasionField.component as DropdownComponent
        assertNotNull(dropdown.options)
        assertTrue(dropdown.options!!.isNotEmpty(), "Le Dropdown doit avoir des options")
    }

    @Test
    fun `troisieme message est UpdateDataModel avec les bonnes cles`() {
        val messages = generator.generateReservationForm()
        assertInstanceOf(UpdateDataModel::class.java, messages[2])
        val msg = messages[2] as UpdateDataModel
        assertEquals("main", msg.surfaceId)
        val keys = msg.contents.map { it.key }.toSet()
        assertTrue("restaurantName" in keys)
        assertTrue("guestName" in keys)
        assertTrue("reservationDate" in keys)
        assertTrue("guestCount" in keys)
    }

    @Test
    fun `guestCount initial est un entier`() {
        val messages = generator.generateReservationForm()
        val dataModel = messages[2] as UpdateDataModel
        val guestCount = dataModel.contents.find { it.key == "guestCount" }
        assertNotNull(guestCount)
        assertNotNull(guestCount!!.valueInt, "guestCount doit être un entier")
    }

    @Test
    fun `quatrieme message est BeginRendering avec root correct`() {
        val messages = generator.generateReservationForm()
        assertInstanceOf(BeginRendering::class.java, messages[3])
        val msg = messages[3] as BeginRendering
        assertEquals("main", msg.surfaceId)
        assertEquals("root", msg.root)
    }

    @Test
    fun `generateReservationFormJsonl produit un JSONL valide`() {
        val jsonl = generator.generateReservationFormJsonl()
        val lines = jsonl.lines().filter { it.isNotBlank() }
        assertEquals(4, lines.size, "JSONL doit avoir 4 lignes")

        // Chaque ligne doit être un JSON valide parseable en A2uiMessage
        for (line in lines) {
            val msg = json.decodeFromString<A2uiMessage>(line)
            assertNotNull(msg)
        }
    }

    @Test
    fun `le JSONL respecte l'ordre du protocole A2UI`() {
        val jsonl = generator.generateReservationFormJsonl()
        val lines = jsonl.lines().filter { it.isNotBlank() }

        assertTrue(lines[0].contains("\"createSurface\""), "Ligne 1 doit être createSurface")
        assertTrue(lines[1].contains("\"surfaceUpdate\""), "Ligne 2 doit être surfaceUpdate")
        assertTrue(lines[2].contains("\"updateDataModel\""), "Ligne 3 doit être updateDataModel")
        assertTrue(lines[3].contains("\"beginRendering\""), "Ligne 4 doit être beginRendering")
    }

    @Test
    fun `le generateur fonctionne avec un surfaceId personnalise`() {
        val customGenerator = A2uiGenerator("reservation-form-42")
        val messages = customGenerator.generateReservationForm()
        val createSurface = messages[0] as CreateSurface
        assertEquals("reservation-form-42", createSurface.surfaceId)
        val beginRendering = messages[3] as BeginRendering
        assertEquals("reservation-form-42", beginRendering.surfaceId)
    }

    @Test
    fun `le Button de soumission a une action submitReservation`() {
        val messages = generator.generateReservationForm()
        val surfaceUpdate = messages[1] as SurfaceUpdate
        val submitBtn = surfaceUpdate.components.find { it.id == "submit-btn" }
        assertNotNull(submitBtn)
        assertInstanceOf(ButtonComponent::class.java, submitBtn!!.component)
        val btn = submitBtn.component as ButtonComponent
        assertNotNull(btn.action)
        assertEquals("submitReservation", btn.action!!.name)
    }
}
