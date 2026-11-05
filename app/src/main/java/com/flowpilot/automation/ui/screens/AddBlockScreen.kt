package com.flowpilot.automation.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Block
import com.flowpilot.automation.data.BlockCatalog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlockScreen(automationId: String, onBack: () -> Unit) {
    val repo = FlowPilotApp.instance.repository
    val scope = rememberCoroutineScope()
    val grouped = BlockCatalog.all.groupBy { it.category }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add step") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            grouped.forEach { (cat, defs) ->
                item { Text(cat, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                items(defs) { def ->
                    ListItem(
                        headlineContent = { Text(def.title) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                val a = repo.get(automationId) ?: return@launch
                                val block = Block(type = def.type, params = def.defaultParams)
                                repo.save(a.copy(steps = a.steps + block))
                                onBack()
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
