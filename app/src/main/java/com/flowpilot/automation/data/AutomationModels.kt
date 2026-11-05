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
        val defaultParams: Map<String, String> = emptyMap(),
        val hasChildren: Boolean = false,
        val hasElse: Boolean = false
    )

    val all: List<Def> = listOf(
        Def(BlockType.TAP_XY, "Tap (x,y)", "UI", mapOf("x" to "540", "y" to "960")),
        Def(BlockType.LONG_PRESS_XY, "Long press (x,y)", "UI", mapOf("x" to "540", "y" to "960", "durationMs" to "800")),
        Def(BlockType.SWIPE, "Swipe / scroll", "UI", mapOf("x1" to "540", "y1" to "1400", "x2" to "540", "y2" to "400", "durationMs" to "400")),
        Def(BlockType.TYPE_TEXT, "Type text", "UI", mapOf("text" to "")),
        Def(BlockType.PRESS_BACK, "Press Back", "UI"),
        Def(BlockType.PRESS_HOME, "Press Home", "UI"),
        Def(BlockType.PRESS_RECENTS, "Press Recents", "UI"),
        Def(BlockType.CLICK_BY_TEXT, "Click by text", "UI", mapOf("text" to "", "partial" to "true")),
        Def(BlockType.CLICK_BY_ID, "Click by view id", "UI", mapOf("viewId" to "")),
        Def(BlockType.CLICK_BY_DESC, "Click by content-desc", "UI", mapOf("desc" to "", "partial" to "true")),
        Def(BlockType.READ_TEXT, "Read element text → var", "UI", mapOf("text" to "", "var" to "result", "partial" to "true")),
        Def(BlockType.READ_SCREEN, "Read whole screen → var", "UI", mapOf("var" to "screenText")),
        Def(BlockType.WAIT_FOR_ELEMENT, "Wait for text/element", "UI", mapOf("text" to "", "timeoutMs" to "10000", "partial" to "true")),
        Def(BlockType.OPEN_APP, "Open app", "App", mapOf("package" to "")),
        Def(BlockType.OPEN_URL, "Open URL", "App", mapOf("url" to "https://")),
        Def(BlockType.SET_CLIPBOARD, "Set clipboard", "App", mapOf("text" to "")),
        Def(BlockType.PASTE, "Paste clipboard", "App"),
        Def(BlockType.SCREENSHOT, "Take screenshot", "App", mapOf("path" to "screenshot.png")),
        Def(BlockType.FIND_IMAGE, "Find image on screen", "Image", mapOf("template" to "", "threshold" to "0.8", "timeoutMs" to "5000", "region" to "", "outX" to "foundX", "outY" to "foundY")),
        Def(BlockType.TAP_FOUND_IMAGE, "Tap found image", "Image", mapOf("template" to "", "threshold" to "0.8", "timeoutMs" to "5000")),
        Def(BlockType.SET_VARIABLE, "Set variable", "Logic", mapOf("name" to "x", "value" to "", "type" to "text")),
        Def(BlockType.MATH, "Math", "Logic", mapOf("expr" to "a + 1", "out" to "result")),
        Def(BlockType.STRING_OP, "String op", "Logic", mapOf("op" to "contains", "a" to "", "b" to "", "out" to "result")),
        Def(BlockType.IF, "If / Else", "Logic", mapOf("op" to "eq", "left" to "", "right" to ""), hasChildren = true, hasElse = true),
        Def(BlockType.WHILE, "While loop", "Logic", mapOf("op" to "lt", "left" to "i", "right" to "10"), hasChildren = true),
        Def(BlockType.FOR, "For / Repeat N", "Logic", mapOf("count" to "5", "var" to "i"), hasChildren = true),
        Def(BlockType.BREAK, "Break", "Logic"),
        Def(BlockType.CONTINUE, "Continue", "Logic"),
        Def(BlockType.WAIT, "Wait / delay", "Logic", mapOf("ms" to "1000")),
        Def(BlockType.RANDOM_DELAY, "Random delay (jitter)", "Logic", mapOf("minMs" to "500", "maxMs" to "1500")),
        Def(BlockType.STOP, "Stop automation", "Logic"),
        Def(BlockType.RUN_AUTOMATION, "Run another automation", "Logic", mapOf("automationId" to "")),
        Def(BlockType.TRY_CATCH, "Try / On error", "Logic", mapOf(), hasChildren = true, hasElse = true),
        Def(BlockType.LOG, "Log message", "Logic", mapOf("message" to "")),
        Def(BlockType.SHOW_NOTIFICATION, "Show notification", "Device", mapOf("title" to "FlowPilot", "text" to "")),
        Def(BlockType.TOAST, "Toast", "Device", mapOf("text" to "")),
        Def(BlockType.TTS, "Text-to-speech", "Device", mapOf("text" to "")),
        Def(BlockType.VIBRATE, "Vibrate", "Device", mapOf("ms" to "300")),
        Def(BlockType.PLAY_SOUND, "Play sound", "Device", mapOf("uri" to "")),
        Def(BlockType.HTTP_REQUEST, "HTTP request", "Device", mapOf("method" to "GET", "url" to "", "body" to "", "out" to "httpBody")),
        Def(BlockType.READ_FILE, "Read text file", "Device", mapOf("path" to "notes.txt", "out" to "fileText")),
        Def(BlockType.WRITE_FILE, "Write text file", "Device", mapOf("path" to "notes.txt", "text" to "", "append" to "false")),
        Def(BlockType.SEND_SMS, "Send SMS (needs permission)", "Device", mapOf("to" to "", "message" to "")),
        Def(BlockType.LAUNCH_INTENT, "Launch intent", "Device", mapOf("action" to "", "data" to "", "package" to "")),
    )

    fun def(type: BlockType) = all.first { it.type == type }
}
