package com.flowpilot.automation.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.*
import com.flowpilot.automation.engine.RunLog
import com.flowpilot.automation.overlay.RecordModeService
import com.flowpilot.automation.service.AutomationService
import com.flowpilot.automation.triggers.ScheduleHelper
import com.flowpilot.automation.ui.components.AppPickerDialog
import com.flowpilot.automation.util.AppPicker
import com.flowpilot.automation.util.BlockSummary
import com.flowpilot.automation.util.ShortcutHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    automationId: String,
    onBack: () -> Unit,
    onEditBlock: (String, String) -> Unit,
    onAddBlock: (String, String?, String) -> Unit
) {
    val repo = FlowPilotApp.instance.repository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var automation by remember {
        mutableStateOf(
            if (automationId == "new") Automation(name = "New automation")
            else repo.get(automationId) ?: Automation(name = "Missing")
        )
    }
    val currentBlock by RunLog.currentBlockId.collectAsState()
    var name by remember { mutableStateOf(automation.name) }
    var showTrigger by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    var undoBlock by remember { mutableStateOf<Pair<List<Block>, Block>?>(null) }

    fun persist(local: Automation = automation.copy(name = name)) {
        scope.launch {
            repo.save(local)
            automation = local
            if (local.trigger is Trigger.Schedule) {
                ScheduleHelper.scheduleOne(FlowPilotApp.instance, local.id)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(if (automationId == "new" || automation.name == "New automation") "New flow" else automation.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        RecordModeService.listener = { blocks ->
                            val merged = automation.copy(name = name, steps = automation.steps + blocks)
                            automation = merged
                            persist(merged)
                            Toast.makeText(context, "Added ${blocks.size} recorded steps", Toast.LENGTH_SHORT).show()
                        }
                        RecordModeService.start(context)
                        Toast.makeText(context, "Recording… use the red bar to stop", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.FiberManualRecord, "Record") }
                    IconButton(onClick = {
                        persist()
                        AutomationService.start(FlowPilotApp.instance, automation.id)
                    }) { Icon(Icons.Default.PlayArrow, "Run") }
                    IconButton(onClick = {
                        scope.launch {
                            val saved = automation.copy(name = name)
                            repo.save(saved)
                            automation = saved
                            if (saved.trigger is Trigger.Schedule) {
                                ScheduleHelper.scheduleOne(FlowPilotApp.instance, saved.id)
                            }
                            onBack()
                        }
                    }) { Icon(Icons.Default.Save, "Save") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch {
                        val saved = automation.copy(name = name)
                        repo.save(saved)
                        automation = saved
                        onAddBlock(saved.id, null, "then")
                    }
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add step") }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Flow name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showTrigger = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text("When: ${triggerSummary(context, automation.trigger)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    ShortcutHelper.pinRunShortcut(context, automation.copy(name = name))
                }) { Text("Pin shortcut") }
            }
            Spacer(Modifier.height(8.dp))
            Text("Steps", style = MaterialTheme.typography.titleSmall)
            if (automation.steps.isEmpty()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlaylistAdd, null, Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No steps yet")
                        Text("Tap Add step or Record to build this flow.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(automation.steps, key = { it.id }) { block ->
                    NestedStepTree(
                        block = block,
                        depth = 0,
                        highlightId = currentBlock,
                        menuFor = menuFor,
                        onMenu = { menuFor = it },
                        onEdit = { onEditBlock(automation.id, it) },
                        onAddInside = { parentId, br ->
                            scope.launch {
                                val saved = automation.copy(name = name)
                                repo.save(saved)
                                automation = saved
                                onAddBlock(saved.id, parentId, br)
                            }
                        },
                        onDuplicate = {
                            automation = automation.copy(steps = BlockTree.duplicate(automation.steps, it))
                            menuFor = null
                        },
                        onDisable = {
                            automation = automation.copy(
                                steps = BlockTree.update(automation.steps, it) { b ->
                                    BlockTree.setEnabledFlag(b, !BlockTree.isDisabled(b))
                                }
                            )
                            menuFor = null
                        },
                        onDelete = { id ->
                            val removed = BlockTree.find(automation.steps, id)
                            val before = automation.steps
                            automation = automation.copy(steps = BlockTree.remove(automation.steps, id))
                            menuFor = null
                            if (removed != null) {
                                undoBlock = before to removed
                                scope.launch {
                                    val result = snackbarHost.showSnackbar(
                                        message = "Step removed",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        undoBlock?.let { (steps, _) ->
                                            automation = automation.copy(steps = steps)
                                        }
                                    }
                                }
                            }
                        },
                        onMoveUp = {
                            automation = automation.copy(steps = BlockTree.moveUp(automation.steps, it))
                            menuFor = null
                        },
                        onMoveDown = {
                            automation = automation.copy(steps = BlockTree.moveDown(automation.steps, it))
                            menuFor = null
                        }
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    if (showTrigger) {
        TriggerDialog(
            current = automation.trigger,
            onDismiss = { showTrigger = false },
            onSave = {
                automation = automation.copy(trigger = it)
                showTrigger = false
            }
        )
    }
}

@Composable
private fun NestedStepTree(
    block: Block,
    depth: Int,
    highlightId: String?,
    menuFor: String?,
    onMenu: (String?) -> Unit,
    onEdit: (String) -> Unit,
    onAddInside: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDisable: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit
) {
    val context = LocalContext.current
    val def = BlockCatalog.def(block.type)
    val disabled = BlockTree.isDisabled(block)
    val highlight = block.id == highlightId
    val indent = (depth * 16).dp

    Card(
        Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 4.dp, bottom = 4.dp)
            .then(if (highlight) Modifier.background(Color(0x334CAF50)) else Modifier)
            .clickable { onEdit(block.id) }
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(categoryIcon(def.category), null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    BlockSummary.sentence(block, context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.onSurface
                )
                if (disabled) {
                    Text("Disabled", style = MaterialTheme.typography.labelSmall)
                }
            }
            Box {
                IconButton(onClick = { onMenu(block.id) }) {
                    Icon(Icons.Default.MoreVert, "Menu")
                }
                DropdownMenu(expanded = menuFor == block.id, onDismissRequest = { onMenu(null) }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { onMenu(null); onEdit(block.id) })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { onDuplicate(block.id) })
                    DropdownMenuItem(
                        text = { Text(if (disabled) "Enable" else "Disable") },
                        onClick = { onDisable(block.id) }
                    )
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { onMoveUp(block.id) })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { onMoveDown(block.id) })
                    if (def.hasChildren) {
                        DropdownMenuItem(
                            text = { Text("Add step inside") },
                            onClick = { onMenu(null); onAddInside(block.id, "then") }
                        )
                    }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(block.id) })
                }
            }
        }
    }

    if (def.hasChildren) {
        Text(
            "Then",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = indent + 16.dp, top = 2.dp)
        )
        block.children.forEach { child ->
            NestedStepTree(
                child, depth + 1, highlightId, menuFor, onMenu, onEdit,
                onAddInside, onDuplicate, onDisable, onDelete, onMoveUp, onMoveDown
            )
        }
        TextButton(
            onClick = { onAddInside(block.id, "then") },
            modifier = Modifier.padding(start = indent + 16.dp)
        ) { Text("+ Add step here") }
    }
    if (def.hasElse) {
        Text(
            if (block.type == BlockType.TRY_CATCH) "On error" else "Else",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = indent + 16.dp, top = 2.dp)
        )
        block.elseChildren.forEach { child ->
            NestedStepTree(
                child, depth + 1, highlightId, menuFor, onMenu, onEdit,
                onAddInside, onDuplicate, onDisable, onDelete, onMoveUp, onMoveDown
            )
        }
        TextButton(
            onClick = { onAddInside(block.id, "else") },
            modifier = Modifier.padding(start = indent + 16.dp)
        ) { Text("+ Add step here") }
    }
}

@Composable
private fun TriggerDialog(current: Trigger, onDismiss: () -> Unit, onSave: (Trigger) -> Unit) {
    val context = LocalContext.current
    var kind by remember {
        mutableStateOf(
            when (current) {
                is Trigger.Manual -> "manual"
                is Trigger.Schedule -> "schedule"
                is Trigger.AppOpened -> "app"
                is Trigger.NotificationReceived -> "notification"
                is Trigger.Boot -> "boot"
                is Trigger.Battery -> "battery"
                is Trigger.Wifi -> "wifi"
            }
        )
    }
    var hour by remember { mutableStateOf((current as? Trigger.Schedule)?.hour?.toString() ?: "8") }
    var minute by remember { mutableStateOf((current as? Trigger.Schedule)?.minute?.toString() ?: "0") }
    var interval by remember { mutableStateOf((current as? Trigger.Schedule)?.intervalMinutes?.toString() ?: "") }
    var pkg by remember { mutableStateOf((current as? Trigger.AppOpened)?.packageName ?: "") }
    var pkgLabel by remember {
        mutableStateOf(
            (current as? Trigger.AppOpened)?.packageName?.let { AppPicker.labelFor(context, it) } ?: ""
        )
    }
    var batBelow by remember { mutableStateOf((current as? Trigger.Battery)?.belowPercent?.toString() ?: "20") }
    var showAppPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("When should this run?") },
        text = {
            Column {
                listOf(
                    "manual" to "Manually (tap Run)",
                    "schedule" to "On a schedule",
                    "app" to "When an app opens",
                    "notification" to "When a notification arrives",
                    "boot" to "After phone restarts",
                    "battery" to "When battery is low",
                    "wifi" to "When Wi-Fi connects"
                ).forEach { (k, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == k, onClick = { kind = k })
                        Text(label)
                    }
                }
                if (kind == "schedule") {
                    OutlinedTextField(hour, { hour = it.filter { ch -> ch.isDigit() } }, label = { Text("Hour (0-23)") })
                    OutlinedTextField(minute, { minute = it.filter { ch -> ch.isDigit() } }, label = { Text("Minute") })
                    OutlinedTextField(interval, { interval = it.filter { ch -> ch.isDigit() } }, label = { Text("Or every N minutes (optional)") })
                }
                if (kind == "app") {
                    OutlinedButton(onClick = { showAppPicker = true }, Modifier.fillMaxWidth()) {
                        Text(if (pkg.isBlank()) "Choose app…" else pkgLabel.ifBlank { pkg })
                    }
                }
                if (kind == "battery") {
                    OutlinedTextField(batBelow, { batBelow = it.filter { ch -> ch.isDigit() } }, label = { Text("Below percent") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t: Trigger = when (kind) {
                    "schedule" -> Trigger.Schedule(
                        hour = hour.toIntOrNull() ?: 8,
                        minute = minute.toIntOrNull() ?: 0,
                        intervalMinutes = interval.toIntOrNull()
                    )
                    "app" -> Trigger.AppOpened(pkg)
                    "notification" -> Trigger.NotificationReceived()
                    "boot" -> Trigger.Boot
                    "battery" -> Trigger.Battery(belowPercent = batBelow.toIntOrNull() ?: 20)
                    "wifi" -> Trigger.Wifi(connected = true)
                    else -> Trigger.Manual
                }
                onSave(t)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPicked = { packageName, label ->
                pkg = packageName
                pkgLabel = label
                showAppPicker = false
            }
        )
    }
}

private fun triggerSummary(context: android.content.Context, t: Trigger): String = when (t) {
    is Trigger.Manual -> "Manual"
    is Trigger.Schedule -> if (t.intervalMinutes != null) "Every ${t.intervalMinutes}m" else "%02d:%02d daily".format(t.hour, t.minute)
    is Trigger.AppOpened -> "App opens: ${AppPicker.labelFor(context, t.packageName)}"
    is Trigger.NotificationReceived -> "Notification"
    is Trigger.Boot -> "After reboot"
    is Trigger.Battery -> "Battery low"
    is Trigger.Wifi -> "Wi-Fi connects"
}
