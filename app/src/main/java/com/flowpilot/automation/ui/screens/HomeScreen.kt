package com.flowpilot.automation.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Trigger
import com.flowpilot.automation.service.AutomationService
import com.flowpilot.automation.util.AppPicker
import com.flowpilot.automation.util.ShortcutHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onOnboarding: () -> Unit,
    onTemplates: () -> Unit = {}
) {
    val repo = FlowPilotApp.instance.repository
    val list by repo.automations.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(list, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) list
        else list.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FlowPilot") },
                actions = {
                    IconButton(onClick = onTemplates) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Templates")
                    }
                    IconButton(onClick = onOnboarding) {
                        Icon(Icons.Default.Checklist, contentDescription = "Setup checklist")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New flow") }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Search flows") }
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.PlaylistAdd, null, Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(if (list.isEmpty()) "No flows yet" else "No matches")
                        Text(
                            if (list.isEmpty()) "Start from a template or tap New flow."
                            else "Try a different search.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (list.isEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onTemplates) { Text("Browse templates") }
                            TextButton(onClick = onOnboarding) { Text("Setup checklist") }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(filtered, key = { it.id }) { a ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onOpen(a.id) }
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        triggerLabel(context, a.trigger) +
                                            if (a.description.isNotBlank()) " · ${a.description}" else "",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text("${a.steps.size} steps", style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(
                                    checked = a.enabled,
                                    onCheckedChange = { en ->
                                        scope.launch { repo.save(a.copy(enabled = en)) }
                                    }
                                )
                                IconButton(onClick = {
                                    AutomationService.start(FlowPilotApp.instance, a.id)
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                                }
                                Box {
                                    IconButton(onClick = { menuFor = a.id }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                                    }
                                    DropdownMenu(expanded = menuFor == a.id, onDismissRequest = { menuFor = null }) {
                                        DropdownMenuItem(
                                            text = { Text("Duplicate") },
                                            onClick = {
                                                menuFor = null
                                                scope.launch {
                                                    val copy = a.copy(
                                                        id = java.util.UUID.randomUUID().toString(),
                                                        name = a.name + " (copy)"
                                                    )
                                                    repo.save(copy)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Pin home shortcut") },
                                            onClick = {
                                                menuFor = null
                                                ShortcutHelper.pinRunShortcut(context, a)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Export JSON") },
                                            onClick = {
                                                menuFor = null
                                                val json = repo.exportJson(a.id)
                                                if (json != null) {
                                                    val cm = FlowPilotApp.instance.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                                        as android.content.ClipboardManager
                                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("automation", json))
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                menuFor = null
                                                scope.launch { repo.delete(a.id) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

private fun triggerLabel(context: android.content.Context, t: Trigger): String = when (t) {
    is Trigger.Manual -> "Manual"
    is Trigger.Schedule -> if (t.intervalMinutes != null) "Every ${t.intervalMinutes}m" else "Daily %02d:%02d".format(t.hour, t.minute)
    is Trigger.AppOpened -> "App: ${AppPicker.labelFor(context, t.packageName)}"
    is Trigger.NotificationReceived -> "Notification"
    is Trigger.Boot -> "On boot"
    is Trigger.Battery -> "Battery"
    is Trigger.Wifi -> "Wi-Fi"
}
