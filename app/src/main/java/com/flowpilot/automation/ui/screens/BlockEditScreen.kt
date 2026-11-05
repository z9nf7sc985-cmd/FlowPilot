package com.flowpilot.automation.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.*
import com.flowpilot.automation.overlay.CoordPickerService
import com.flowpilot.automation.overlay.ElementPickerService
import com.flowpilot.automation.overlay.ImageCropPickerService
import com.flowpilot.automation.overlay.SwipeRecorderService
import com.flowpilot.automation.service.AutomationService
import com.flowpilot.automation.ui.components.AppPickerDialog
import com.flowpilot.automation.util.AppPicker
import com.flowpilot.automation.util.BlockSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockEditScreen(automationId: String, blockId: String, onBack: () -> Unit) {
    val repo = FlowPilotApp.instance.repository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val automation = repo.get(automationId)
    val block = automation?.let { BlockTree.find(it.steps, blockId) }
    if (automation == null || block == null) {
        Text("Step not found")
        return
    }
    val def = BlockCatalog.def(block.type)
    var params by remember { mutableStateOf(block.params.toMutableMap()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appLabel by remember {
        mutableStateOf(
            params["package"]?.takeIf { it.isNotBlank() }?.let { AppPicker.labelFor(context, it) } ?: ""
        )
    }
    val varNames = remember(automation) {
        (automation.variables.keys + listOf("battery", "time", "hour", "minute", "clipboard", "foundX", "foundY", "screenText", "result"))
            .distinct().sorted()
    }

    fun save(andBack: Boolean = true) {
        scope.launch {
            val updatedBlock = block.copy(params = params.toMap())
            val steps = BlockTree.update(automation.steps, blockId) { updatedBlock }
            repo.save(automation.copy(steps = steps))
            if (andBack) onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(def.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        save(andBack = false)
                        // Run only this step via a temporary one-step automation
                        scope.launch {
                            val single = automation.copy(
                                id = "tmp-step-${block.id}",
                                name = "Single step",
                                steps = listOf(block.copy(params = params.toMap())),
                                trigger = Trigger.Manual,
                                enabled = true
                            )
                            repo.save(single)
                            AutomationService.start(FlowPilotApp.instance, single.id)
                            Toast.makeText(context, "Running this step…", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Default.PlayArrow, "Run this step") }
                    IconButton(onClick = { save() }) { Icon(Icons.Default.Save, "Save") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(BlockSummary.sentence(block.copy(params = params), context), style = MaterialTheme.typography.titleSmall)
            if (def.description.isNotBlank()) {
                Text(def.description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))

            // App picker for package fields
            if (params.containsKey("package") || block.type == BlockType.OPEN_APP) {
                Text("App", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { showAppPicker = true }, Modifier.fillMaxWidth()) {
                    Text(if (params["package"].isNullOrBlank()) "Choose app…" else appLabel.ifBlank { params["package"]!! })
                }
                Spacer(Modifier.height(8.dp))
            }

            // Pick-from-screen tools
            when (block.type) {
                BlockType.CLICK_BY_TEXT, BlockType.CLICK_BY_ID, BlockType.CLICK_BY_DESC,
                BlockType.WAIT_FOR_ELEMENT, BlockType.READ_TEXT -> {
                    Button(onClick = {
                        ElementPickerService.listener = { picked ->
                            params = params.toMutableMap().also { m ->
                                when (block.type) {
                                    BlockType.CLICK_BY_ID -> if (picked.viewId.isNotBlank()) m["viewId"] = picked.viewId
                                    BlockType.CLICK_BY_DESC -> if (picked.contentDesc.isNotBlank()) m["desc"] = picked.contentDesc
                                    else -> {
                                        val t = picked.text.ifBlank { picked.contentDesc }
                                        if (t.isNotBlank()) m["text"] = t
                                        if (picked.viewId.isNotBlank() && block.type == BlockType.CLICK_BY_TEXT) {
                                            // keep text; user can switch type later
                                        }
                                    }
                                }
                            }
                            Toast.makeText(context, "Filled from screen", Toast.LENGTH_SHORT).show()
                        }
                        ElementPickerService.start(context)
                        Toast.makeText(context, "Overlay opened — tap a highlighted element", Toast.LENGTH_LONG).show()
                    }, Modifier.fillMaxWidth()) { Text("Pick element from screen") }
                }
                BlockType.TAP_XY, BlockType.LONG_PRESS_XY -> {
                    Button(onClick = {
                        CoordPickerService.listener = { pt ->
                            params = params.toMutableMap().also {
                                it["x"] = pt.x.toString()
                                it["y"] = pt.y.toString()
                                it["xPercent"] = "%.2f".format(pt.xPercent)
                                it["yPercent"] = "%.2f".format(pt.yPercent)
                            }
                            Toast.makeText(context, "Point ${pt.x}, ${pt.y}", Toast.LENGTH_SHORT).show()
                        }
                        CoordPickerService.start(context)
                    }, Modifier.fillMaxWidth()) { Text("Pick point on screen") }
                }
                BlockType.SWIPE -> {
                    Button(onClick = {
                        SwipeRecorderService.listener = { s ->
                            params = params.toMutableMap().also {
                                it["x1"] = s.x1.toString(); it["y1"] = s.y1.toString()
                                it["x2"] = s.x2.toString(); it["y2"] = s.y2.toString()
                                it["durationMs"] = s.durationMs.toString()
                            }
                        }
                        SwipeRecorderService.start(context)
                    }, Modifier.fillMaxWidth()) { Text("Record swipe") }
                    Button(onClick = {
                        CoordPickerService.listener = { pt ->
                            params = params.toMutableMap().also {
                                it["x1"] = pt.x.toString(); it["y1"] = pt.y.toString()
                            }
                        }
                        CoordPickerService.start(context)
                    }, Modifier.fillMaxWidth()) { Text("Pick start point") }
                }
                BlockType.FIND_IMAGE, BlockType.TAP_FOUND_IMAGE -> {
                    Button(onClick = {
                        ImageCropPickerService.listener = { name ->
                            params = params.toMutableMap().also { it["template"] = name }
                            Toast.makeText(context, "Template: $name", Toast.LENGTH_SHORT).show()
                        }
                        ImageCropPickerService.start(context)
                    }, Modifier.fillMaxWidth()) { Text("Pick image (screenshot + crop)") }
                    val templates = repo.listTemplates()
                    if (templates.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded, { expanded = it }) {
                            OutlinedTextField(
                                value = params["template"].orEmpty(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Saved template") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                            )
                            ExposedDropdownMenu(expanded, { expanded = false }) {
                                templates.forEach { t ->
                                    DropdownMenuItem(text = { Text(t) }, onClick = {
                                        params = params.toMutableMap().also { it["template"] = t }
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                }
                else -> {}
            }

            // Condition wizard for IF / WHILE
            if (block.type == BlockType.IF || block.type == BlockType.WHILE) {
                Spacer(Modifier.height(8.dp))
                Text("Condition", style = MaterialTheme.typography.titleSmall)
                val kinds = listOf(
                    "" to "Compare values",
                    "element_exists" to "If element with text exists",
                    "image_found" to "If image is found",
                    "battery" to "If battery level…",
                    "time_of_day" to "If time between…",
                    "app_foreground" to "If app is in foreground"
                )
                var kindExpanded by remember { mutableStateOf(false) }
                val kind = params["kind"].orEmpty()
                ExposedDropdownMenuBox(kindExpanded, { kindExpanded = it }) {
                    OutlinedTextField(
                        value = kinds.firstOrNull { it.first == kind }?.second ?: "Compare values",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Template") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindExpanded) }
                    )
                    ExposedDropdownMenu(kindExpanded, { kindExpanded = false }) {
                        kinds.forEach { (k, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                params = params.toMutableMap().also { it["kind"] = k }
                                kindExpanded = false
                            })
                        }
                    }
                }
                when (kind) {
                    "element_exists" -> {
                        OutlinedTextField(
                            params["text"].orEmpty(),
                            { params = params.toMutableMap().also { m -> m["text"] = it } },
                            label = { Text("Text to look for") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = {
                            ElementPickerService.listener = { picked ->
                                val t = picked.text.ifBlank { picked.contentDesc }
                                if (t.isNotBlank()) params = params.toMutableMap().also { it["text"] = t }
                            }
                            ElementPickerService.start(context)
                        }, Modifier.fillMaxWidth()) { Text("Pick element") }
                    }
                    "image_found" -> {
                        OutlinedTextField(
                            params["template"].orEmpty(),
                            { params = params.toMutableMap().also { m -> m["template"] = it } },
                            label = { Text("Template file") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "battery" -> {
                        OperatorDropdown(params["op"].orEmpty().ifBlank { "lt" }) {
                            params = params.toMutableMap().also { m -> m["op"] = it; m["left"] = "{{battery}}" }
                        }
                        OutlinedTextField(
                            params["right"].orEmpty().ifBlank { "20" },
                            { params = params.toMutableMap().also { m -> m["right"] = it; m["left"] = "{{battery}}" } },
                            label = { Text("Percent") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "time_of_day" -> {
                        OutlinedTextField(
                            params["from"].orEmpty().ifBlank { "08:00" },
                            { params = params.toMutableMap().also { m -> m["from"] = it } },
                            label = { Text("From (HH:mm)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            params["to"].orEmpty().ifBlank { "18:00" },
                            { params = params.toMutableMap().also { m -> m["to"] = it } },
                            label = { Text("To (HH:mm)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "app_foreground" -> {
                        OutlinedButton(onClick = { showAppPicker = true }, Modifier.fillMaxWidth()) {
                            Text(if (params["package"].isNullOrBlank()) "Choose app…" else appLabel.ifBlank { params["package"]!! })
                        }
                    }
                    else -> {
                        VariableDropdown("Left value", params["left"].orEmpty(), varNames) {
                            params = params.toMutableMap().also { m -> m["left"] = it }
                        }
                        OperatorDropdown(params["op"].orEmpty().ifBlank { "eq" }) {
                            params = params.toMutableMap().also { m -> m["op"] = it }
                        }
                        if (params["op"] !in listOf("isEmpty", "truthy")) {
                            VariableDropdown("Right value", params["right"].orEmpty(), varNames) {
                                params = params.toMutableMap().also { m -> m["right"] = it }
                            }
                        }
                    }
                }
                Text("Tip: use {{variableName}} to insert a variable.", style = MaterialTheme.typography.bodySmall)
            }

            // Generic param fields (skip ones handled specially)
            val skipKeys = buildSet {
                add("__disabled")
                if (params.containsKey("package") || block.type == BlockType.OPEN_APP) add("package")
                if (block.type == BlockType.IF || block.type == BlockType.WHILE) {
                    addAll(listOf("kind", "op", "left", "right", "text", "template", "partial"))
                }
            }
            val keys = (def.defaultParams.keys + params.keys).distinct().filter { it !in skipKeys }
            keys.forEach { key ->
                when (key) {
                    "op" -> OperatorDropdown(params[key].orEmpty()) {
                        params = params.toMutableMap().also { m -> m[key] = it }
                    }
                    "method" -> {
                        var exp by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(exp, { exp = it }) {
                            OutlinedTextField(
                                params[key].orEmpty().ifBlank { "GET" }, {}, readOnly = true,
                                label = { Text("HTTP method") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp) }
                            )
                            ExposedDropdownMenu(exp, { exp = false }) {
                                listOf("GET", "POST", "PUT", "DELETE").forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = {
                                        params = params.toMutableMap().also { it[key] = m }; exp = false
                                    })
                                }
                            }
                        }
                    }
                    "partial", "append" -> {
                        Row {
                            Checkbox(
                                checked = params[key].equals("true", true),
                                onCheckedChange = {
                                    params = params.toMutableMap().also { m -> m[key] = if (it) "true" else "false" }
                                }
                            )
                            Text(key.replaceFirstChar { it.uppercase() })
                        }
                    }
                    "type" -> {
                        var exp by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(exp, { exp = it }) {
                            OutlinedTextField(
                                params[key].orEmpty().ifBlank { "text" }, {}, readOnly = true,
                                label = { Text("Variable type") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp) }
                            )
                            ExposedDropdownMenu(exp, { exp = false }) {
                                listOf("text", "number", "bool").forEach { t ->
                                    DropdownMenuItem(text = { Text(t) }, onClick = {
                                        params = params.toMutableMap().also { it[key] = t }; exp = false
                                    })
                                }
                            }
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = params[key] ?: "",
                            onValueChange = { params = params.toMutableMap().also { m -> m[key] = it } },
                            label = { Text(humanLabel(key)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            singleLine = key !in listOf("text", "message", "body", "expr", "url"),
                            supportingText = {
                                val help = helpFor(key)
                                if (help != null) Text(help)
                            }
                        )
                    }
                }
            }

            if (keys.isEmpty() && block.type != BlockType.IF && block.type != BlockType.WHILE &&
                !params.containsKey("package") && block.type != BlockType.OPEN_APP
            ) {
                Text("This step has no settings — it's ready to use.")
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { save() }, Modifier.fillMaxWidth()) { Text("Save step") }
            OutlinedButton(onClick = {
                save(andBack = false)
                AutomationService.start(FlowPilotApp.instance, automationId)
                Toast.makeText(context, "Running flow from the start…", Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth()) { Text("Run from here (whole flow)") }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPicked = { packageName, label ->
                params = params.toMutableMap().also { it["package"] = packageName }
                appLabel = label
                showAppPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperatorDropdown(value: String, onChange: (String) -> Unit) {
    val ops = listOf(
        "eq" to "equals",
        "neq" to "does not equal",
        "lt" to "less than",
        "lte" to "less or equal",
        "gt" to "greater than",
        "gte" to "greater or equal",
        "contains" to "contains",
        "startsWith" to "starts with",
        "isEmpty" to "is empty",
        "truthy" to "is true"
    )
    var exp by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(exp, { exp = it }) {
        OutlinedTextField(
            value = ops.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Operator") },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp) }
        )
        ExposedDropdownMenu(exp, { exp = false }) {
            ops.forEach { (k, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onChange(k); exp = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariableDropdown(label: String, value: String, vars: List<String>, onChange: (String) -> Unit) {
    var exp by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { exp = true }) { Text("{ }") }
        }
    )
    DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
        vars.forEach { v ->
            DropdownMenuItem(
                text = { Text("{{$v}}") },
                onClick = { onChange("{{$v}}"); exp = false }
            )
        }
    }
}

private fun humanLabel(key: String): String = when (key) {
    "ms", "timeoutMs", "durationMs", "minMs", "maxMs" -> key.replace("Ms", " (ms)").replaceFirstChar { it.uppercase() }
    "viewId" -> "View id"
    "outX" -> "Save X to variable"
    "outY" -> "Save Y to variable"
    "out" -> "Save result to variable"
    "var" -> "Variable name"
    "count" -> "Times to repeat"
    "threshold" -> "Match threshold (0–1)"
    "template" -> "Image template file"
    "xPercent", "yPercent" -> key
    else -> key.replaceFirstChar { it.uppercase() }
}

private fun helpFor(key: String): String? = when (key) {
    "threshold" -> "0.8 is a good starting match strength"
    "timeoutMs" -> "How long to keep looking before giving up"
    "partial" -> "Allow partial text matches"
    else -> null
}
