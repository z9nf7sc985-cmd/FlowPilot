package com.flowpilot.automation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.*
import com.flowpilot.automation.engine.RunLog
import com.flowpilot.automation.service.AutomationService
import com.flowpilot.automation.triggers.ScheduleHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    automationId: String,
    onBack: () -> Unit,
    onEditBlock: (String, String) -> Unit,
    onAddBlock: (String) -> Unit
) {
    val repo = FlowPilotApp.instance.repository
    val scope = rememberCoroutineScope()
    var automation by remember {
        mutableStateOf(
            if (automationId == "new") Automation(name = "New automation")
            else repo.get(automationId) ?: Automation(name = "Missing")
        )
    }
    val currentBlock by RunLog.currentBlockId.collectAsState()
    var name by remember { mutableStateOf(automation.name) }
    var showTrigger by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (automationId == "new") "New flow" else "Edit flow") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
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
            FloatingActionButton(onClick = {
                scope.launch {
                    val saved = automation.copy(name = name)
                    repo.save(saved)
                    automation = saved
                    onAddBlock(saved.id)
                }
            }) { Icon(Icons.Default.Add, "Add step") }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showTrigger = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Trigger: ${triggerSummary(automation.trigger)}")
            }
            Spacer(Modifier.height(8.dp))
            Text("Steps", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(automation.steps, key = { _, b -> b.id }) { index, block ->
                    val highlight = block.id == currentBlock
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (highlight) Modifier.background(Color(0x334CAF50))
                                else Modifier
                            )
                            .clickable { onEditBlock(automation.id, block.id) }
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}.", Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(BlockCatalog.def(block.type).title)
                                Text(block.params.entries.take(3).joinToString { "${it.key}=${it.value}" },
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = {
                                if (index > 0) {
                                    val steps = automation.steps.toMutableList()
                                    val tmp = steps[index - 1]
                                    steps[index - 1] = steps[index]
                                    steps[index] = tmp
                                    automation = automation.copy(steps = steps)
                                }
                            }) { Icon(Icons.Default.KeyboardArrowUp, "Up") }
                            IconButton(onClick = {
                                if (index < automation.steps.lastIndex) {
                                    val steps = automation.steps.toMutableList()
                                    val tmp = steps[index + 1]
                                    steps[index + 1] = steps[index]
                                    steps[index] = tmp
                                    automation = automation.copy(steps = steps)
                                }
                            }) { Icon(Icons.Default.KeyboardArrowDown, "Down") }
                            IconButton(onClick = {
                                val steps = automation.steps.toMutableList()
                                val dup = block.copy(id = java.util.UUID.randomUUID().toString())
                                steps.add(index + 1, dup)
                                automation = automation.copy(steps = steps)
                            }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
                            IconButton(onClick = {
                                automation = automation.copy(steps = automation.steps.filterNot { it.id == block.id })
                            }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                    // Nested preview
                    if (block.children.isNotEmpty()) {
                        Text("  └ then ${block.children.size} child step(s)", style = MaterialTheme.typography.labelSmall)
                    }
                    if (block.elseChildren.isNotEmpty()) {
                        Text("  └ else ${block.elseChildren.size} step(s)", style = MaterialTheme.typography.labelSmall)
                    }
                }
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
private fun TriggerDialog(current: Trigger, onDismiss: () -> Unit, onSave: (Trigger) -> Unit) {
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
    var batBelow by remember { mutableStateOf((current as? Trigger.Battery)?.belowPercent?.toString() ?: "20") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trigger") },
        text = {
            Column {
                listOf("manual", "schedule", "app", "notification", "boot", "battery", "wifi").forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == k, onClick = { kind = k })
                        Text(k)
                    }
                }
                if (kind == "schedule") {
                    OutlinedTextField(hour, { hour = it }, label = { Text("Hour (0-23)") })
                    OutlinedTextField(minute, { minute = it }, label = { Text("Minute") })
                    OutlinedTextField(interval, { interval = it }, label = { Text("Or interval minutes (blank=daily)") })
                }
                if (kind == "app") {
                    OutlinedTextField(pkg, { pkg = it }, label = { Text("Package name") })
                }
                if (kind == "battery") {
                    OutlinedTextField(batBelow, { batBelow = it }, label = { Text("Below %") })
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
}

private fun triggerSummary(t: Trigger): String = when (t) {
    is Trigger.Manual -> "Manual"
    is Trigger.Schedule -> if (t.intervalMinutes != null) "Every ${t.intervalMinutes}m" else "%02d:%02d daily".format(t.hour, t.minute)
    is Trigger.AppOpened -> "App opened: ${t.packageName}"
    is Trigger.NotificationReceived -> "Notification"
    is Trigger.Boot -> "Boot"
    is Trigger.Battery -> "Battery"
    is Trigger.Wifi -> "Wi-Fi"
}
