package com.flowpilot.automation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.engine.LogEntry
import com.flowpilot.automation.engine.RunLog
import com.flowpilot.automation.service.AutomationService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunLogScreen() {
    val entries by RunLog.entries.collectAsState()
    val running by RunLog.runningAutomationId.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (running != null) "Run log (running)" else "Run log") },
                actions = {
                    if (running != null) {
                        IconButton(onClick = {
                            RunLog.globalKill = true
                            AutomationService.stop(FlowPilotApp.instance)
                        }) { Icon(Icons.Default.Stop, "Stop") }
                    }
                    IconButton(onClick = { RunLog.clear() }) {
                        Icon(Icons.Default.Delete, "Clear")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), state = listState) {
            items(entries) { e ->
                val color = when (e.level) {
                    LogEntry.Level.ERROR -> Color(0xFFFF8A80)
                    LogEntry.Level.WARN -> Color(0xFFFFD54F)
                    LogEntry.Level.DEBUG -> Color(0xFF90CAF9)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    "${e.timeLabel()} [${e.level}] ${e.message}",
                    color = color,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
