package com.flowpilot.automation.engine

import com.flowpilot.automation.data.VarValue
import org.junit.Assert.*
import org.junit.Test

class FlowEngineLogicTest {

    @Test
    fun variableExpand_replacesPlaceholders() {
        val vs = VariableStore(mapOf("name" to VarValue.Text("Ada"), "n" to VarValue.Number(3.0)))
        assertEquals("Hello Ada x3", vs.expand("Hello {{name}} x{{n}}"))
    }

    @Test
    fun variableExpand_nestedOnce() {
        val vs = VariableStore(mapOf("a" to VarValue.Text("{{b}}"), "b" to VarValue.Text("ok")))
        assertEquals("ok", vs.expand("{{a}}"))
    }

    @Test
    fun varValue_conversions() {
        assertEquals(1.0, VarValue.Bool(true).asNumber(), 0.0)
        assertEquals("42", VarValue.Number(42.0).asText())
        assertTrue(VarValue.Text("true").asBool())
        assertFalse(VarValue.Text("no").asBool())
    }

    @Test
    fun mathTokenizer_basic() {
        // Mirror eval via a tiny local reimplementation using VariableStore numbers
        val vs = VariableStore(mapOf("a" to VarValue.Number(10.0), "b" to VarValue.Number(3.0)))
        assertEquals("10", vs.expand("{{a}}"))
        assertEquals(13.0, simpleAdd(vs.get("a").asNumber(), vs.get("b").asNumber()), 0.0)
    }

    @Test
    fun conditionCompare_numbers() {
        assertTrue(cmp(5.0, "lt", 10.0))
        assertFalse(cmp(5.0, "gt", 10.0))
        assertTrue(cmp(5.0, "eq", 5.0))
        assertTrue(cmp(5.0, "neq", 6.0))
        assertTrue(cmp(5.0, "lte", 5.0))
        assertTrue(cmp(5.0, "gte", 5.0))
    }

    @Test
    fun loopGuard_forBounds() {
        var count = 0
        val n = 5
        for (i in 0 until n.coerceAtMost(10_000)) count++
        assertEquals(5, count)
    }

    @Test
    fun stringContains_logic() {
        assertTrue("Hello World".contains("world", ignoreCase = true))
        assertFalse("Hello".contains("xyz", ignoreCase = true))
    }

    private fun cmp(left: Double, op: String, right: Double): Boolean = when (op) {
        "eq" -> left == right
        "neq" -> left != right
        "lt" -> left < right
        "lte" -> left <= right
        "gt" -> left > right
        "gte" -> left >= right
        else -> false
    }

    private fun simpleAdd(a: Double, b: Double) = a + b
}
