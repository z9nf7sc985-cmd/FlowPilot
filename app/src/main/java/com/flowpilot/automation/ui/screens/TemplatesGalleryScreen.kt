package com.flowpilot.automation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.ExampleAutomations
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesGalleryScreen(
    onBack: () -> Unit,
    onOpened: (String) -> Unit
) {
    val repo = FlowPilotApp.instance.repository
    val scope = rememberCoroutineScope()
    val templates = remember { ExampleAutomations.gallery() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            item {
                Text(
                    "One-tap starters. “Use template” copies it into your flows so you can edit freely.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            items(templates, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        if (t.description.isNotBlank()) {
                            Text(t.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${t.steps.size} steps · ${if (t.enabled) "ready" else "starts disabled"}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val copy = t.copy(
                                        id = UUID.randomUUID().toString(),
                                        name = t.name,
                                        enabled = t.enabled
                                    )
                                    repo.save(copy)
                                    onOpened(copy.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Use template") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
