package com.flowpilot.automation.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Serializable
data class Automation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger = Trigger.Manual,
    val steps: List<Block> = emptyList(),
    val variables: Map<String, VarValue> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
sealed class Trigger {
    @Serializable @SerialName("manual")
    data object Manual : Trigger()

    @Serializable @SerialName("schedule")
    data class Schedule(
        val hour: Int = 8,
        val minute: Int = 0,
        val intervalMinutes: Int? = null, // if set, repeats every N minutes instead of daily
        val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7) // 1=Mon .. 7=Sun
    ) : Trigger()

    @Serializable @SerialName("app_opened")
    data class AppOpened(val packageName: String) : Trigger()

    @Serializable @SerialName("notification")
    data class NotificationReceived(
        val packageName: String? = null,
        val titleContains: String? = null,
        val textContains: String? = null
    ) : Trigger()

    @Serializable @SerialName("boot")
    data object Boot : Trigger()

    @Serializable @SerialName("battery")
    data class Battery(
        val belowPercent: Int? = null,
        val abovePercent: Int? = null,
        val onCharging: Boolean? = null
    ) : Trigger()

    @Serializable @SerialName("wifi")
    data class Wifi(
        val connected: Boolean? = null,
        val ssidContains: String? = null
    ) : Trigger()
}

@Serializable
sealed class VarValue {
    @Serializable @SerialName("text")
    data class Text(val value: String = "") : VarValue()
    @Serializable @SerialName("number")
    data class Number(val value: Double = 0.0) : VarValue()
    @Serializable @SerialName("bool")
    data class Bool(val value: Boolean = false) : VarValue()

    fun asText(): String = when (this) {
        is Text -> value
        is Number -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Bool -> value.toString()
    }

    fun asNumber(): Double = when (this) {
        is Number -> value
        is Text -> value.toDoubleOrNull() ?: 0.0
        is Bool -> if (value) 1.0 else 0.0
    }

    fun asBool(): Boolean = when (this) {
        is Bool -> value
        is Number -> value != 0.0
        is Text -> value.equals("true", true) || value == "1"
    }

    companion object {
        fun fromJson(el: JsonElement?): VarValue {
            val p = el?.jsonPrimitive ?: return Text("")
            p.booleanOrNull?.let { return Bool(it) }
            p.doubleOrNull?.let { return Number(it) }
            return Text(p.contentOrNull ?: "")
        }
    }
}

@Serializable
data class Block(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType,
    val params: Map<String, String> = emptyMap(),
    val children: List<Block> = emptyList(),
    val elseChildren: List<Block> = emptyList(),
    val elseIfBranches: List<ElseIfBranch> = emptyList()
)

@Serializable
data class ElseIfBranch(
    val id: String = UUID.randomUUID().toString(),
    val condition: Map<String, String> = emptyMap(),
    val children: List<Block> = emptyList()
)

@Serializable
enum class BlockType {
    // Actions - UI
    TAP_XY,
    LONG_PRESS_XY,
    SWIPE,
    TYPE_TEXT,
    PRESS_BACK,
    PRESS_HOME,
    PRESS_RECENTS,
    CLICK_BY_TEXT,
    CLICK_BY_ID,
    CLICK_BY_DESC,
    READ_TEXT,
    READ_SCREEN,
    WAIT_FOR_ELEMENT,
    OPEN_APP,
    OPEN_URL,
    SET_CLIPBOARD,
    PASTE,
    SCREENSHOT,

    // Image
    FIND_IMAGE,
    TAP_FOUND_IMAGE,

    // Variables / logic
    SET_VARIABLE,
    MATH,
    STRING_OP,
    IF,
    WHILE,
    FOR,
    BREAK,
    CONTINUE,
    WAIT,
    STOP,
    RUN_AUTOMATION,
    TRY_CATCH,
    LOG,
    RANDOM_DELAY,

    // Other
    SHOW_NOTIFICATION,
    TOAST,
    TTS,
    VIBRATE,
    PLAY_SOUND,
    HTTP_REQUEST,
    READ_FILE,
    WRITE_FILE,
    SEND_SMS,
    LAUNCH_INTENT
}

fun Block.param(key: String, default: String = ""): String = params[key] ?: default
fun Block.paramInt(key: String, default: Int = 0): Int = params[key]?.toIntOrNull() ?: default
fun Block.paramDouble(key: String, default: Double = 0.0): Double = params[key]?.toDoubleOrNull() ?: default
fun Block.paramBool(key: String, default: Boolean = false): Boolean =
    params[key]?.let { it.equals("true", true) || it == "1" } ?: default

object BlockCatalog {
    data class Def(
        val type: BlockType,
        val title: String,
        val category: String,
        val description: String = "",
        val defaultParams: Map<String, String> = emptyMap(),
        val hasChildren: Boolean = false,
        val hasElse: Boolean = false
    )

    val categories = listOf(
        "Apps", "Tap & Gestures", "Screen & Images", "Text",
        "Logic", "Loops", "Variables", "Device", "Notifications", "Network & Files"
    )

    val all: List<Def> = listOf(
        Def(BlockType.OPEN_APP, "Open app", "Apps", "Launch an installed app from a searchable list", mapOf("package" to "")),
        Def(BlockType.OPEN_URL, "Open URL", "Apps", "Open a link in the browser", mapOf("url" to "https://")),
        Def(BlockType.LAUNCH_INTENT, "Launch intent", "Apps", "Fire a custom Android intent", mapOf("action" to "", "data" to "", "package" to "")),
        Def(BlockType.RUN_AUTOMATION, "Run another flow", "Apps", "Start a different automation", mapOf("automationId" to "")),

        Def(BlockType.TAP_XY, "Tap point", "Tap & Gestures", "Tap coordinates (use Pick point)", mapOf("x" to "540", "y" to "960")),
        Def(BlockType.LONG_PRESS_XY, "Long press", "Tap & Gestures", "Press and hold a point", mapOf("x" to "540", "y" to "960", "durationMs" to "800")),
        Def(BlockType.SWIPE, "Swipe", "Tap & Gestures", "Swipe between two points (or record a swipe)", mapOf("x1" to "540", "y1" to "1400", "x2" to "540", "y2" to "400", "durationMs" to "400")),
        Def(BlockType.CLICK_BY_TEXT, "Tap by text", "Tap & Gestures", "Tap a button/label that matches text", mapOf("text" to "", "partial" to "true")),
        Def(BlockType.CLICK_BY_ID, "Tap by id", "Tap & Gestures", "Tap a view by resource id", mapOf("viewId" to "")),
        Def(BlockType.CLICK_BY_DESC, "Tap by description", "Tap & Gestures", "Tap by accessibility content-description", mapOf("desc" to "", "partial" to "true")),
        Def(BlockType.PRESS_BACK, "Press Back", "Tap & Gestures", "System Back button"),
        Def(BlockType.PRESS_HOME, "Press Home", "Tap & Gestures", "Go to home screen"),
        Def(BlockType.PRESS_RECENTS, "Press Recents", "Tap & Gestures", "Open recent apps"),

        Def(BlockType.FIND_IMAGE, "Find image", "Screen & Images", "Look for a saved template on screen", mapOf("template" to "", "threshold" to "0.8", "timeoutMs" to "5000", "region" to "", "outX" to "foundX", "outY" to "foundY")),
        Def(BlockType.TAP_FOUND_IMAGE, "Tap found image", "Screen & Images", "Find a template and tap its center", mapOf("template" to "", "threshold" to "0.8", "timeoutMs" to "5000")),
        Def(BlockType.SCREENSHOT, "Take screenshot", "Screen & Images", "Save a screenshot to a file", mapOf("path" to "screenshot.png")),
        Def(BlockType.WAIT_FOR_ELEMENT, "Wait for text", "Screen & Images", "Pause until text appears", mapOf("text" to "", "timeoutMs" to "10000", "partial" to "true")),
        Def(BlockType.READ_SCREEN, "Read whole screen", "Screen & Images", "Copy all visible text into a variable", mapOf("var" to "screenText")),

        Def(BlockType.TYPE_TEXT, "Type text", "Text", "Type into the focused field", mapOf("text" to "")),
        Def(BlockType.READ_TEXT, "Read element text", "Text", "Find matching text and store it", mapOf("text" to "", "var" to "result", "partial" to "true")),
        Def(BlockType.SET_CLIPBOARD, "Set clipboard", "Text", "Copy text to the clipboard", mapOf("text" to "")),
        Def(BlockType.PASTE, "Paste clipboard", "Text", "Paste into the focused field"),
        Def(BlockType.STRING_OP, "String tools", "Text", "Contains, starts with, replace, etc.", mapOf("op" to "contains", "a" to "", "b" to "", "out" to "result")),

        Def(BlockType.IF, "If / Else", "Logic", "Run steps only when a condition is true", mapOf("kind" to "", "op" to "eq", "left" to "", "right" to ""), hasChildren = true, hasElse = true),
        Def(BlockType.TRY_CATCH, "Try / On error", "Logic", "Catch errors and run a fallback", mapOf(), hasChildren = true, hasElse = true),
        Def(BlockType.WAIT, "Wait", "Logic", "Pause for a fixed time", mapOf("ms" to "1000")),
        Def(BlockType.RANDOM_DELAY, "Random wait", "Logic", "Pause a random amount (jitter)", mapOf("minMs" to "500", "maxMs" to "1500")),
        Def(BlockType.STOP, "Stop", "Logic", "Stop this automation now"),
        Def(BlockType.LOG, "Log message", "Logic", "Write to the run log", mapOf("message" to "")),

        Def(BlockType.WHILE, "While loop", "Loops", "Repeat while a condition is true", mapOf("op" to "lt", "left" to "i", "right" to "10"), hasChildren = true),
        Def(BlockType.FOR, "Repeat N times", "Loops", "Run child steps a fixed number of times", mapOf("count" to "5", "var" to "i"), hasChildren = true),
        Def(BlockType.BREAK, "Break", "Loops", "Exit the current loop"),
        Def(BlockType.CONTINUE, "Continue", "Loops", "Skip to the next loop iteration"),

        Def(BlockType.SET_VARIABLE, "Set variable", "Variables", "Store a value you can reuse", mapOf("name" to "x", "value" to "", "type" to "text")),
        Def(BlockType.MATH, "Math", "Variables", "Evaluate a simple expression", mapOf("expr" to "a + 1", "out" to "result")),

        Def(BlockType.TOAST, "Toast", "Device", "Show a short on-screen message", mapOf("text" to "")),
        Def(BlockType.TTS, "Speak", "Device", "Read text aloud", mapOf("text" to "")),
        Def(BlockType.VIBRATE, "Vibrate", "Device", "Buzz the phone", mapOf("ms" to "300")),
        Def(BlockType.PLAY_SOUND, "Play sound", "Device", "Play an audio URI", mapOf("uri" to "")),
        Def(BlockType.SEND_SMS, "Send SMS", "Device", "Send a text message (needs permission)", mapOf("to" to "", "message" to "")),

        Def(BlockType.SHOW_NOTIFICATION, "Show notification", "Notifications", "Post a status-bar notification", mapOf("title" to "FlowPilot", "text" to "")),

        Def(BlockType.HTTP_REQUEST, "HTTP request", "Network & Files", "Call a URL (GET/POST)", mapOf("method" to "GET", "url" to "", "body" to "", "out" to "httpBody")),
        Def(BlockType.READ_FILE, "Read file", "Network & Files", "Read a text file into a variable", mapOf("path" to "notes.txt", "out" to "fileText")),
        Def(BlockType.WRITE_FILE, "Write file", "Network & Files", "Write or append text to a file", mapOf("path" to "notes.txt", "text" to "", "append" to "false")),
    )

    fun def(type: BlockType) = all.first { it.type == type }
}
