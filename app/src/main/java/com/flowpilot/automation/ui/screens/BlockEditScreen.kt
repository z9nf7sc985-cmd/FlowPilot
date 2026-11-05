package com.flowpilot.automation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Block
import com.flowpilot.automation.data.BlockCatalog
import com.flowpilot.automation.data.BlockType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockEditScreen(automationId: String, blockId: String, onBack: () -> Unit) {
    val repo = FlowPilotApp.instance.repository
    val scope = rememberCoroutineScope()
    val automation = repo.get(automationId)
    val block = automation?.steps?.find { it.id == blockId }
    if (automation == null || block == null) {
        Text("Block not found")
        return
    }
    val def = BlockCatalog.def(block.type)
    var params by remember { mutableStateOf(block.params.toMutableMap()) }

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
                        scope.launch {
                            val updatedBlock = block.copy(params = params.toMap())
                            val steps = automation.steps.map { if (it.id == blockId) updatedBlock else it }
                            repo.save(automation.copy(steps = steps))
                            onBack()
                        }
                    }) { Icon(Icons.Default.Save, "Save") }
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
            Text("Type: ${block.type}", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))
            val keys = (def.defaultParams.keys + params.keys).distinct()
            keys.forEach { key ->
                OutlinedTextField(
                    value = params[key] ?: "",
                    onValueChange = { params = params.toMutableMap().also { m -> m[key] = it } },
                    label = { Text(key) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = key !in listOf("text", "message", "body", "expr")
                )
            }
            if (keys.isEmpty()) {
                Text("This block has no parameters.")
            }
            if (block.type == BlockType.IF || block.type == BlockType.WHILE) {
                Spacer(Modifier.height(8.dp))
                Text("Condition ops: eq, neq, lt, lte, gt, gte, contains, startsWith, isEmpty, truthy",
                    style = MaterialTheme.typography.bodySmall)
                Text("Use {{varName}} in values. Special: set kind=element_exists|image_found|battery|time_of_day|app_foreground",
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Text("Nested children: edit JSON export or duplicate parent and use Try/If blocks. Child editing UI ships in a later build; for IF/WHILE/FOR/TRY, add children via Import JSON or the helper buttons below.")
            Spacer(Modifier.height(8.dp))
            if (def.hasChildren) {
                Button(onClick = {
                    scope.launch {
                        val child = Block(type = BlockType.LOG, params = mapOf("message" to "then"))
                        val updated = block.copy(children = block.children + child, params = params.toMap())
                        val steps = automation.steps.map { if (it.id == blockId) updated else it }
                        repo.save(automation.copy(steps = steps))
                        onBack()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Add child LOG step (then branch)") }
            }
            if (def.hasElse) {
                Button(onClick = {
                    scope.launch {
                        val child = Block(type = BlockType.LOG, params = mapOf("message" to "else"))
                        val updated = block.copy(elseChildren = block.elseChildren + child, params = params.toMap())
                        val steps = automation.steps.map { if (it.id == blockId) updated else it }
                        repo.save(automation.copy(steps = steps))
                        onBack()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Add child LOG step (else / on-error)") }
            }
        }
    }
}
