package com.flowpilot.automation.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Block
import com.flowpilot.automation.data.BlockCatalog
import com.flowpilot.automation.data.BlockTree
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlockScreen(
    automationId: String,
    onBack: () -> Unit,
    parentBlockId: String? = null,
    branch: String = "then"
) {
    val repo = FlowPilotApp.instance.repository
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val grouped = remember(query) {
        val q = query.trim().lowercase()
        BlockCatalog.all
            .filter {
                q.isEmpty() ||
                    it.title.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.category.lowercase().contains(q)
            }
            .groupBy { it.category }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            parentBlockId != null && branch == "else" -> "Add step (else)"
                            parentBlockId != null -> "Add step (inside)"
                            else -> "Add step"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Search steps") },
                placeholder = { Text("tap, wait, open app…") }
            )
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                BlockCatalog.categories.forEach { cat ->
                    val defs = grouped[cat] ?: return@forEach
                    item {
                        Text(
                            cat,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                    }
                    items(defs, key = { it.type.name }) { def ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    scope.launch {
                                        val a = repo.get(automationId) ?: return@launch
                                        val block = Block(type = def.type, params = def.defaultParams)
                                        val steps = if (parentBlockId.isNullOrBlank()) {
                                            a.steps + block
                                        } else {
                                            BlockTree.insertChild(a.steps, parentBlockId, branch, block)
                                        }
                                        repo.save(a.copy(steps = steps))
                                        onBack()
                                    }
                                }
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(categoryIcon(cat), null, Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(def.title, style = MaterialTheme.typography.titleSmall)
                                    if (def.description.isNotBlank()) {
                                        Text(def.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Icon(Icons.Default.Add, null)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

fun categoryIcon(category: String): ImageVector = when (category) {
    "Apps" -> Icons.Default.Apps
    "Tap & Gestures" -> Icons.Default.TouchApp
    "Screen & Images" -> Icons.Default.ImageSearch
    "Text" -> Icons.Default.TextFields
    "Logic" -> Icons.Default.AccountTree
    "Loops" -> Icons.Default.Loop
    "Variables" -> Icons.Default.DataObject
    "Device" -> Icons.Default.PhoneAndroid
    "Notifications" -> Icons.Default.Notifications
    "Network & Files" -> Icons.Default.Cloud
    else -> Icons.Default.Extension
}
