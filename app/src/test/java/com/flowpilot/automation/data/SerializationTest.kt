package com.flowpilot.automation.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
