package com.flowpilot.automation.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    @Test
    fun roundTrip_automation() {
        val a = ExampleAutomations.all().first()
        val encoded = json.encodeToString(a)
        val decoded = json.decodeFromString<Automation>(encoded)
        assertEquals(a.id, decoded.id)
        assertEquals(a.name, decoded.name)
        assertEquals(a.steps.size, decoded.steps.size)
        assertTrue(encoded.contains("FIND_IMAGE") || encoded.contains("find_image") || encoded.contains("\"type\""))
    }

    @Test
    fun allExamples_serialize() {
        ExampleAutomations.all().forEach {
            val s = json.encodeToString(it)
            val back = json.decodeFromString<Automation>(s)
            assertEquals(it.trigger::class, back.trigger::class)
        }
    }

    @Test
    fun import_assignsFreshIdentity() {
        val a = ExampleAutomations.all().first()
        val encoded = json.encodeToString(a)
        val decoded = json.decodeFromString<Automation>(encoded)
        val imported = decoded.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = decoded.name + " (imported)"
        )
        assertNotEquals(a.id, imported.id)
        assertTrue(imported.name.endsWith("(imported)"))
        val again = json.decodeFromString<Automation>(json.encodeToString(imported))
        assertEquals(imported.id, again.id)
        assertEquals(imported.steps.size, again.steps.size)
    }

    @Test
    fun nestedIf_elseChildren_roundTrip() {
        val child = Block(type = BlockType.LOG, params = mapOf("message" to "then"))
        val elseChild = Block(type = BlockType.LOG, params = mapOf("message" to "else"))
        val a = Automation(
            name = "nested",
            steps = listOf(
                Block(
                    type = BlockType.IF,
                    params = mapOf("op" to "eq", "left" to "1", "right" to "1"),
                    children = listOf(child),
                    elseChildren = listOf(elseChild)
                )
            )
        )
        val back = json.decodeFromString<Automation>(json.encodeToString(a))
        assertEquals(1, back.steps[0].children.size)
        assertEquals(1, back.steps[0].elseChildren.size)
        assertEquals("then", back.steps[0].children[0].params["message"])
        assertEquals("else", back.steps[0].elseChildren[0].params["message"])
    }
}
