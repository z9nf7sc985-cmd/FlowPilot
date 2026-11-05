package com.flowpilot.automation.util

import android.content.Context
import com.flowpilot.automation.data.Block
import com.flowpilot.automation.data.BlockType
import com.flowpilot.automation.data.param

object BlockSummary {
    fun sentence(block: Block, context: Context? = null): String {
        fun p(key: String, default: String = ""): String = block.param(key, default)
        return when (block.type) {
            BlockType.TAP_XY -> "Tap at (${p("x")}, ${p("y")})"
            BlockType.LONG_PRESS_XY -> "Long-press at (${p("x")}, ${p("y")})"
            BlockType.SWIPE -> "Swipe from (${p("x1")}, ${p("y1")}) to (${p("x2")}, ${p("y2")})"
            BlockType.TYPE_TEXT -> {
                val t = p("text")
                if (t.isBlank()) "Type text" else "Type \"$t\""
            }
            BlockType.PRESS_BACK -> "Press Back"
            BlockType.PRESS_HOME -> "Press Home"
            BlockType.PRESS_RECENTS -> "Press Recents"
            BlockType.CLICK_BY_TEXT -> {
                val t = p("text")
                if (t.isBlank()) "Tap element by text" else "Tap on \"$t\""
            }
            BlockType.CLICK_BY_ID -> "Tap element id \"${p("viewId")}\""
            BlockType.CLICK_BY_DESC -> "Tap element \"${p("desc")}\""
            BlockType.READ_TEXT -> "Read text matching \"${p("text")}\" → ${p("var", "result")}"
            BlockType.READ_SCREEN -> "Read whole screen → ${p("var", "screenText")}"
            BlockType.WAIT_FOR_ELEMENT -> "Wait for \"${p("text")}\" (up to ${p("timeoutMs", "10000")} ms)"
            BlockType.OPEN_APP -> {
                val pkg = p("package")
                val name = context?.let { AppPicker.labelFor(it, pkg) } ?: pkg
                if (pkg.isBlank()) "Open app" else "Open $name"
            }
            BlockType.OPEN_URL -> "Open URL ${p("url")}"
            BlockType.SET_CLIPBOARD -> "Copy \"${p("text")}\" to clipboard"
            BlockType.PASTE -> "Paste clipboard"
            BlockType.SCREENSHOT -> "Take screenshot → ${p("path", "screenshot.png")}"
            BlockType.FIND_IMAGE -> "Find image \"${p("template")}\" on screen"
            BlockType.TAP_FOUND_IMAGE -> "Tap image \"${p("template")}\" when found"
            BlockType.SET_VARIABLE -> "Set ${p("name", "x")} = ${p("value")}"
            BlockType.MATH -> "Math ${p("expr")} → ${p("out", "result")}"
            BlockType.STRING_OP -> "String ${p("op")}: ${p("a")} / ${p("b")} → ${p("out")}"
            BlockType.IF -> ifSentence(block)
            BlockType.WHILE -> "While ${condShort(block)} …"
            BlockType.FOR -> "Repeat ${p("count", "5")} times (as ${p("var", "i")})"
            BlockType.BREAK -> "Break loop"
            BlockType.CONTINUE -> "Continue loop"
            BlockType.WAIT -> "Wait ${p("ms", "1000")} ms"
            BlockType.RANDOM_DELAY -> "Wait ${p("minMs")}-${p("maxMs")} ms (random)"
            BlockType.STOP -> "Stop automation"
            BlockType.RUN_AUTOMATION -> "Run another automation"
            BlockType.TRY_CATCH -> "Try … on error …"
            BlockType.LOG -> "Log: ${p("message")}"
            BlockType.SHOW_NOTIFICATION -> "Notify: ${p("title")} — ${p("text")}"
            BlockType.TOAST -> "Toast: ${p("text")}"
            BlockType.TTS -> "Speak: ${p("text")}"
            BlockType.VIBRATE -> "Vibrate ${p("ms", "300")} ms"
            BlockType.PLAY_SOUND -> "Play sound"
            BlockType.HTTP_REQUEST -> "${p("method", "GET")} ${p("url")}"
            BlockType.READ_FILE -> "Read file ${p("path")} → ${p("out")}"
            BlockType.WRITE_FILE -> "Write file ${p("path")}"
            BlockType.SEND_SMS -> "Send SMS to ${p("to")}"
            BlockType.LAUNCH_INTENT -> "Launch intent ${p("action")}"
        }
    }

    private fun ifSentence(block: Block): String {
        val kind = block.param("kind")
        return when (kind) {
            "element_exists" -> "If element \"${block.param("text")}\" exists → …"
            "image_found" -> "If image \"${block.param("template")}\" is found → …"
            "battery" -> "If battery ${block.param("op", "lt")} ${block.param("right", "20")}% → …"
            "time_of_day" -> "If time between ${block.param("from", block.param("left"))} and ${block.param("to", block.param("right"))} → …"
            "app_foreground" -> "If app ${block.param("package")} is in foreground → …"
            else -> "If ${condShort(block)} → …"
        }
    }

    private fun condShort(block: Block): String {
        val op = block.param("op", "eq")
        val left = block.param("left")
        val right = block.param("right")
        val opWord = when (op) {
            "eq" -> "="
            "neq" -> "≠"
            "lt" -> "<"
            "lte" -> "≤"
            "gt" -> ">"
            "gte" -> "≥"
            "contains" -> "contains"
            "startsWith" -> "starts with"
            "isEmpty" -> "is empty"
            "truthy" -> "is true"
            else -> op
        }
        return when (op) {
            "isEmpty", "truthy" -> "$left $opWord"
            else -> "$left $opWord $right"
        }
    }
}
