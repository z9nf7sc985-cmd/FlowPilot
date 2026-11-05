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
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Trigger
import com.flowpilot.automation.service.AutomationService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onOnboarding: () -> Unit
) {
    val repo = FlowPilotApp.instance.repository
    val list by repo.automations.collectAsState()
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FlowPilot") },
                actions = {
                    IconButton(onClick = onOnboarding) {
                        Icon(Icons.Default.Info, contentDescription = "Setup help")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = "New automation")
            }
        }
    ) { pad ->
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No automations yet. Tap + to create one.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
                items(list, key = { it.id }) { a ->
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
                                    triggerLabel(a.trigger) + if (a.description.isNotBlank()) " · ${a.description}" else "",
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
            }
        }
    }
}

private fun triggerLabel(t: Trigger): String = when (t) {
    is Trigger.Manual -> "Manual"
    is Trigger.Schedule -> if (t.intervalMinutes != null) "Every ${t.intervalMinutes}m" else "Daily %02d:%02d".format(t.hour, t.minute)
    is Trigger.AppOpened -> "App: ${t.packageName}"
    is Trigger.NotificationReceived -> "Notification"
    is Trigger.Boot -> "On boot"
    is Trigger.Battery -> "Battery"
    is Trigger.Wifi -> "Wi-Fi"
}
