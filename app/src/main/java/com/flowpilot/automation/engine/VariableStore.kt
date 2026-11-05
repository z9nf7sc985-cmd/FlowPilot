package com.flowpilot.automation.engine

import com.flowpilot.automation.data.VarValue
import java.util.concurrent.ConcurrentHashMap

class VariableStore(initial: Map<String, VarValue> = emptyMap()) {
    private val map = ConcurrentHashMap<String, VarValue>(initial)

    fun get(name: String): VarValue = map[name] ?: VarValue.Text("")
    fun set(name: String, value: VarValue) { map[name] = value }
    fun setText(name: String, v: String) = set(name, VarValue.Text(v))
    fun setNumber(name: String, v: Double) = set(name, VarValue.Number(v))
    fun setBool(name: String, v: Boolean) = set(name, VarValue.Bool(v))
    fun snapshot(): Map<String, VarValue> = HashMap(map)

    /** Expand {{var}} placeholders in a string. */
    fun expand(input: String): String {
        if (!input.contains("{{")) return input
        var out = input
        val regex = Regex("""\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}""")
        repeat(8) {
            val next = regex.replace(out) { m ->
                val key = m.groupValues[1]
                map[key]?.asText() ?: ""
            }
            if (next == out) return next
            out = next
        }
        return out
    }
}
