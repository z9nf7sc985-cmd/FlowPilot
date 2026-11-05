package com.flowpilot.automation.engine

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.R
import com.flowpilot.automation.a11y.FlowAccessibilityService
import com.flowpilot.automation.data.Automation
import com.flowpilot.automation.data.Block
import com.flowpilot.automation.data.BlockType
import com.flowpilot.automation.data.VarValue
import com.flowpilot.automation.data.param
import com.flowpilot.automation.data.paramBool
import com.flowpilot.automation.data.paramDouble
import com.flowpilot.automation.data.paramInt
import com.flowpilot.automation.image.ImageMatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

sealed class FlowSignal {
    data object Break : FlowSignal()
    data object Continue : FlowSignal()
    data object Stop : FlowSignal()
}

class FlowEngine(
    private val context: Context,
    private val vars: VariableStore,
    private val automation: Automation,
    private val onSubroutine: (suspend (String) -> Unit)? = null
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var tts: TextToSpeech? = null

    suspend fun run(): Boolean {
        seedSystemVars()
        RunLog.setRunning(automation.id)
        RunLog.log("Started: ${automation.name}", automationId = automation.id)
        return try {
            val signal = runBlocks(automation.steps)
            signal !is FlowSignal.Stop || true
            RunLog.log("Finished: ${automation.name}", automationId = automation.id)
            true
        } catch (e: Exception) {
            RunLog.log("Error: ${e.message}", LogEntry.Level.ERROR, automation.id)
            false
        } finally {
            RunLog.setCurrentBlock(null)
            RunLog.setRunning(null)
            tts?.shutdown()
        }
    }

    private fun seedSystemVars() {
        automation.variables.forEach { (k, v) -> vars.set(k, v) }
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        vars.setNumber("battery", level.toDouble())
        vars.setText("time", java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date()))
        vars.setNumber("hour", java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY).toDouble())
        vars.setNumber("minute", java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE).toDouble())
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            vars.setText("clipboard", clip)
        } catch (_: Exception) {
            vars.setText("clipboard", "")
        }
    }

    private suspend fun runBlocks(blocks: List<Block>): FlowSignal? {
        for (block in blocks) {
            if (!coroutineContext.isActive || RunLog.globalKill) return FlowSignal.Stop
            val signal = runBlock(block)
            when (signal) {
                is FlowSignal.Break, is FlowSignal.Continue, is FlowSignal.Stop -> return signal
                null -> {}
            }
        }
        return null
    }

    private suspend fun runBlock(block: Block): FlowSignal? {
        if (block.paramBool("__disabled", false)) {
            RunLog.log("Skipped (disabled)", blockId = block.id, automationId = automation.id)
            return null
        }
        RunLog.setCurrentBlock(block.id)
        val a11y = FlowAccessibilityService.instance
        when (block.type) {
            BlockType.TAP_XY -> {
                val x = vars.expand(block.param("x")).toFloatOrNull() ?: 0f
                val y = vars.expand(block.param("y")).toFloatOrNull() ?: 0f
                requireA11y(a11y).tap(x, y)
                RunLog.log("Tap $x,$y", blockId = block.id, automationId = automation.id)
            }
            BlockType.LONG_PRESS_XY -> {
                val x = vars.expand(block.param("x")).toFloatOrNull() ?: 0f
                val y = vars.expand(block.param("y")).toFloatOrNull() ?: 0f
                requireA11y(a11y).longPress(x, y, block.paramLong("durationMs", 800))
            }
            BlockType.SWIPE -> {
                requireA11y(a11y).swipe(
                    vars.expand(block.param("x1")).toFloatOrNull() ?: 0f,
                    vars.expand(block.param("y1")).toFloatOrNull() ?: 0f,
                    vars.expand(block.param("x2")).toFloatOrNull() ?: 0f,
                    vars.expand(block.param("y2")).toFloatOrNull() ?: 0f,
                    block.paramLong("durationMs", 400)
                )
            }
            BlockType.TYPE_TEXT -> {
                requireA11y(a11y).typeText(vars.expand(block.param("text")))
            }
            BlockType.PRESS_BACK -> requireA11y(a11y).performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            BlockType.PRESS_HOME -> requireA11y(a11y).performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            BlockType.PRESS_RECENTS -> requireA11y(a11y).performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            BlockType.CLICK_BY_TEXT -> {
                val ok = requireA11y(a11y).clickByText(vars.expand(block.param("text")), block.paramBool("partial", true))
                if (!ok) RunLog.log("clickByText miss", LogEntry.Level.WARN, automation.id, block.id)
            }
            BlockType.CLICK_BY_ID -> {
                val ok = requireA11y(a11y).clickByViewId(vars.expand(block.param("viewId")))
                if (!ok) RunLog.log("clickById miss", LogEntry.Level.WARN, automation.id, block.id)
            }
            BlockType.CLICK_BY_DESC -> {
                val ok = requireA11y(a11y).clickByDesc(vars.expand(block.param("desc")), block.paramBool("partial", true))
                if (!ok) RunLog.log("clickByDesc miss", LogEntry.Level.WARN, automation.id, block.id)
            }
            BlockType.READ_TEXT -> {
                val text = requireA11y(a11y).findText(vars.expand(block.param("text")), block.paramBool("partial", true)) ?: ""
                vars.setText(block.param("var", "result"), text)
            }
            BlockType.READ_SCREEN -> {
                val text = requireA11y(a11y).readScreenText()
                vars.setText(block.param("var", "screenText"), text)
            }
            BlockType.WAIT_FOR_ELEMENT -> {
                val timeout = block.paramLong("timeoutMs", 10000)
                val needle = vars.expand(block.param("text"))
                val partial = block.paramBool("partial", true)
                val start = System.currentTimeMillis()
                var found = false
                while (System.currentTimeMillis() - start < timeout) {
                    if (RunLog.globalKill) return FlowSignal.Stop
                    if (requireA11y(a11y).findText(needle, partial) != null) { found = true; break }
                    delay(250)
                }
                vars.setBool("elementFound", found)
                if (!found) RunLog.log("waitForElement timeout: $needle", LogEntry.Level.WARN, automation.id, block.id)
            }
            BlockType.OPEN_APP -> {
                val pkg = vars.expand(block.param("package"))
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                    ?: throw IllegalStateException("No launch intent for $pkg")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
            }
            BlockType.OPEN_URL -> {
                val url = vars.expand(block.param("url"))
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
            BlockType.SET_CLIPBOARD -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("flowpilot", vars.expand(block.param("text"))))
            }
            BlockType.PASTE -> requireA11y(a11y).paste()
            BlockType.SCREENSHOT -> {
                val path = vars.expand(block.param("path", "screenshot.png"))
                val file = File(context.filesDir, path)
                val ok = requireA11y(a11y).takeScreenshotToFile(file)
                vars.setBool("screenshotOk", ok)
                vars.setText("screenshotPath", file.absolutePath)
            }
            BlockType.FIND_IMAGE -> {
                val result = findImage(block)
                vars.setNumber(block.param("outX", "foundX"), result?.first?.toDouble() ?: -1.0)
                vars.setNumber(block.param("outY", "foundY"), result?.second?.toDouble() ?: -1.0)
                vars.setBool("imageFound", result != null)
                vars.setNumber("imageConfidence", result?.third ?: 0.0)
            }
            BlockType.TAP_FOUND_IMAGE -> {
                val result = findImage(block)
                if (result != null) {
                    requireA11y(a11y).tap(result.first.toFloat(), result.second.toFloat())
                    vars.setBool("imageFound", true)
                } else {
                    vars.setBool("imageFound", false)
                    RunLog.log("tapFoundImage miss", LogEntry.Level.WARN, automation.id, block.id)
                }
            }
            BlockType.SET_VARIABLE -> {
                val name = vars.expand(block.param("name"))
                val value = vars.expand(block.param("value"))
                when (block.param("type", "text")) {
                    "number" -> vars.setNumber(name, value.toDoubleOrNull() ?: 0.0)
                    "bool" -> vars.setBool(name, value.equals("true", true) || value == "1")
                    else -> vars.setText(name, value)
                }
            }
            BlockType.MATH -> {
                val expr = vars.expand(block.param("expr"))
                val result = evalMath(expr)
                vars.setNumber(block.param("out", "result"), result)
            }
            BlockType.STRING_OP -> {
                val a = vars.expand(block.param("a"))
                val b = vars.expand(block.param("b"))
                val out = block.param("out", "result")
                when (block.param("op", "contains")) {
                    "contains" -> vars.setBool(out, a.contains(b, ignoreCase = true))
                    "equals" -> vars.setBool(out, a.equals(b, ignoreCase = true))
                    "startsWith" -> vars.setBool(out, a.startsWith(b, ignoreCase = true))
                    "endsWith" -> vars.setBool(out, a.endsWith(b, ignoreCase = true))
                    "length" -> vars.setNumber(out, a.length.toDouble())
                    "upper" -> vars.setText(out, a.uppercase(Locale.US))
                    "lower" -> vars.setText(out, a.lowercase(Locale.US))
                    "replace" -> vars.setText(out, a.replace(b, vars.expand(block.param("c"))))
                    "substring" -> {
                        val start = block.paramInt("start", 0)
                        val end = block.paramInt("end", a.length).coerceAtMost(a.length)
                        vars.setText(out, a.substring(start.coerceAtLeast(0), end))
                    }
                    else -> vars.setText(out, a)
                }
            }
            BlockType.IF -> {
                if (evalCondition(block.params)) {
                    val s = runBlocks(block.children)
                    if (s != null) return s
                } else {
                    var matched = false
                    for (branch in block.elseIfBranches) {
                        if (evalCondition(branch.condition)) {
                            matched = true
                            val s = runBlocks(branch.children)
                            if (s != null) return s
                            break
                        }
                    }
                    if (!matched) {
                        val s = runBlocks(block.elseChildren)
                        if (s != null) return s
                    }
                }
            }
            BlockType.WHILE -> {
                var guard = 0
                while (evalCondition(block.params) && guard < 10_000) {
                    if (RunLog.globalKill) return FlowSignal.Stop
                    when (val s = runBlocks(block.children)) {
                        is FlowSignal.Break -> break
                        is FlowSignal.Continue -> { guard++; continue }
                        is FlowSignal.Stop -> return s
                        null -> {}
                    }
                    guard++
                }
            }
            BlockType.FOR -> {
                val count = vars.expand(block.param("count")).toIntOrNull() ?: block.paramInt("count", 1)
                val name = block.param("var", "i")
                for (i in 0 until count.coerceAtMost(10_000)) {
                    if (RunLog.globalKill) return FlowSignal.Stop
                    vars.setNumber(name, i.toDouble())
                    when (val s = runBlocks(block.children)) {
                        is FlowSignal.Break -> break
                        is FlowSignal.Continue -> continue
                        is FlowSignal.Stop -> return s
                        null -> {}
                    }
                }
            }
            BlockType.BREAK -> return FlowSignal.Break
            BlockType.CONTINUE -> return FlowSignal.Continue
            BlockType.WAIT -> delay(block.paramLong("ms", 1000))
            BlockType.RANDOM_DELAY -> {
                val min = block.paramLong("minMs", 500)
                val max = block.paramLong("maxMs", 1500).coerceAtLeast(min)
                delay(Random.nextLong(min, max + 1))
            }
            BlockType.STOP -> return FlowSignal.Stop
            BlockType.RUN_AUTOMATION -> {
                val id = vars.expand(block.param("automationId"))
                onSubroutine?.invoke(id)
            }
            BlockType.TRY_CATCH -> {
                try {
                    val s = runBlocks(block.children)
                    if (s != null) return s
                } catch (e: Exception) {
                    vars.setText("lastError", e.message ?: "error")
                    RunLog.log("Caught: ${e.message}", LogEntry.Level.WARN, automation.id, block.id)
                    val s = runBlocks(block.elseChildren)
                    if (s != null) return s
                }
            }
            BlockType.LOG -> RunLog.log(vars.expand(block.param("message")), automationId = automation.id, blockId = block.id)
            BlockType.SHOW_NOTIFICATION -> {
                val title = vars.expand(block.param("title", "FlowPilot"))
                val text = vars.expand(block.param("text"))
                val n = NotificationCompat.Builder(context, FlowPilotApp.CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_stop)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n)
            }
            BlockType.TOAST -> {
                val text = vars.expand(block.param("text"))
                android.os.Handler(context.mainLooper).post {
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
            BlockType.TTS -> {
                val text = vars.expand(block.param("text"))
                speak(text)
            }
            BlockType.VIBRATE -> {
                val ms = block.paramLong("ms", 300)
                val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") vib.vibrate(ms)
                }
            }
            BlockType.PLAY_SOUND -> {
                val uriStr = vars.expand(block.param("uri"))
                val uri = if (uriStr.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else Uri.parse(uriStr)
                RingtoneManager.getRingtone(context, uri)?.play()
            }
            BlockType.HTTP_REQUEST -> {
                val method = block.param("method", "GET").uppercase(Locale.US)
                val url = vars.expand(block.param("url"))
                val body = vars.expand(block.param("body"))
                val reqBuilder = Request.Builder().url(url)
                when (method) {
                    "POST" -> reqBuilder.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    "PUT" -> reqBuilder.put(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    else -> reqBuilder.get()
                }
                http.newCall(reqBuilder.build()).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    vars.setText(block.param("out", "httpBody"), respBody)
                    vars.setNumber("httpStatus", resp.code.toDouble())
                }
            }
            BlockType.READ_FILE -> {
                val path = vars.expand(block.param("path"))
                val file = File(context.filesDir, path)
                vars.setText(block.param("out", "fileText"), if (file.exists()) file.readText() else "")
            }
            BlockType.WRITE_FILE -> {
                val path = vars.expand(block.param("path"))
                val text = vars.expand(block.param("text"))
                val file = File(context.filesDir, path)
                file.parentFile?.mkdirs()
                if (block.paramBool("append")) file.appendText(text) else file.writeText(text)
            }
            BlockType.SEND_SMS -> {
                val to = vars.expand(block.param("to"))
                val msg = vars.expand(block.param("message"))
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    SmsManager.getDefault().sendTextMessage(to, null, msg, null, null)
                    RunLog.log("SMS sent to $to", automationId = automation.id)
                } else {
                    RunLog.log("SEND_SMS permission not granted", LogEntry.Level.WARN, automation.id, block.id)
                }
            }
            BlockType.LAUNCH_INTENT -> {
                val action = vars.expand(block.param("action"))
                val data = vars.expand(block.param("data"))
                val pkg = vars.expand(block.param("package"))
                val i = Intent(action.ifBlank { Intent.ACTION_VIEW })
                if (data.isNotBlank()) i.data = Uri.parse(data)
                if (pkg.isNotBlank()) i.setPackage(pkg)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        }
        return null
    }

    private suspend fun findImage(block: Block): Triple<Int, Int, Double>? {
        val a11y = requireA11y(FlowAccessibilityService.instance)
        val templateName = vars.expand(block.param("template"))
        val threshold = block.paramDouble("threshold", 0.8)
        val timeout = block.paramLong("timeoutMs", 5000)
        val region = vars.expand(block.param("region"))
        val templateFile = File(FlowPilotApp.instance.repository.templatesDir(), templateName)
        if (!templateFile.exists()) {
            RunLog.log("Template missing: $templateName", LogEntry.Level.ERROR, automation.id, block.id)
            return null
        }
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            if (RunLog.globalKill) return null
            val screen = a11y.captureBitmap() ?: run { delay(200); null } ?: continue
            val match = ImageMatcher.findTemplate(screen, templateFile, threshold, region)
            screen.recycle()
            if (match != null) return match
            delay(200)
        }
        return null
    }

    private fun evalCondition(params: Map<String, String>): Boolean {
        // Special condition kinds
        when (params["kind"]) {
            "element_exists" -> {
                val a11y = FlowAccessibilityService.instance ?: return false
                return a11y.findText(vars.expand(params["text"] ?: ""), params["partial"] != "false") != null
            }
            "image_found" -> return vars.get("imageFound").asBool()
            "time_of_day" -> {
                val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val m = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
                val now = h * 60 + m
                val from = parseHm(params["from"] ?: "00:00")
                val to = parseHm(params["to"] ?: "23:59")
                return now in from..to
            }
            "battery" -> {
                val level = vars.get("battery").asNumber()
                val op = params["op"] ?: "lt"
                val right = params["right"]?.toDoubleOrNull() ?: 20.0
                return compare(level, op, right)
            }
            "app_foreground" -> {
                val pkg = vars.expand(params["package"] ?: "")
                return FlowAccessibilityService.instance?.foregroundPackage() == pkg
            }
        }
        val left = vars.expand(params["left"] ?: "")
        val right = vars.expand(params["right"] ?: "")
        val op = params["op"] ?: "eq"
        val leftNum = left.toDoubleOrNull()
        val rightNum = right.toDoubleOrNull()
        return if (leftNum != null && rightNum != null && op in setOf("eq", "neq", "lt", "lte", "gt", "gte")) {
            compare(leftNum, op, rightNum)
        } else when (op) {
            "eq" -> left.equals(right, ignoreCase = true)
            "neq" -> !left.equals(right, ignoreCase = true)
            "contains" -> left.contains(right, ignoreCase = true)
            "startsWith" -> left.startsWith(right, ignoreCase = true)
            "isEmpty" -> left.isEmpty()
            "isNotEmpty" -> left.isNotEmpty()
            "truthy" -> left.equals("true", true) || left == "1"
            else -> left == right
        }
    }

    private fun compare(left: Double, op: String, right: Double): Boolean = when (op) {
        "eq" -> left == right
        "neq" -> left != right
        "lt" -> left < right
        "lte" -> left <= right
        "gt" -> left > right
        "gte" -> left >= right
        else -> false
    }

    private fun parseHm(s: String): Int {
        val p = s.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: 0
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun evalMath(expr: String): Double {
        // Very small expression evaluator: supports + - * / and numbers / variables already expanded
        val tokens = tokenize(expr)
        return parseExpr(tokens)
    }

    private fun tokenize(expr: String): MutableList<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c in "+-*/()" -> { out += c.toString(); i++ }
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    out += expr.substring(start, i)
                }
                else -> {
                    val start = i
                    while (i < expr.length && !expr[i].isWhitespace() && expr[i] !in "+-*/()") i++
                    val name = expr.substring(start, i)
                    out += vars.get(name).asNumber().toString()
                }
            }
        }
        return out
    }

    private fun parseExpr(tokens: MutableList<String>): Double {
        var v = parseTerm(tokens)
        while (tokens.isNotEmpty() && tokens.first() in listOf("+", "-")) {
            val op = tokens.removeAt(0)
            val r = parseTerm(tokens)
            v = if (op == "+") v + r else v - r
        }
        return v
    }

    private fun parseTerm(tokens: MutableList<String>): Double {
        var v = parseFactor(tokens)
        while (tokens.isNotEmpty() && tokens.first() in listOf("*", "/")) {
            val op = tokens.removeAt(0)
            val r = parseFactor(tokens)
            v = if (op == "*") v * r else if (r == 0.0) 0.0 else v / r
        }
        return v
    }

    private fun parseFactor(tokens: MutableList<String>): Double {
        if (tokens.isEmpty()) return 0.0
        val t = tokens.removeAt(0)
        return when (t) {
            "(" -> {
                val v = parseExpr(tokens)
                if (tokens.isNotEmpty() && tokens.first() == ")") tokens.removeAt(0)
                v
            }
            "-" -> -parseFactor(tokens)
            else -> t.toDoubleOrNull() ?: 0.0
        }
    }

    private fun requireA11y(svc: FlowAccessibilityService?): FlowAccessibilityService =
        svc ?: throw IllegalStateException("Enable FlowPilot Accessibility Service in Settings")

    private fun Block.paramLong(key: String, default: Long): Long =
        params[key]?.toLongOrNull() ?: default

    private fun speak(text: String) {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fp")
                }
            }
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fp")
        }
    }
}
