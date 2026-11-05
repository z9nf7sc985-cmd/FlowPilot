package com.flowpilot.automation.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.a11y.FlowAccessibilityService
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(onBack: () -> Unit) {
    val repo = FlowPilotApp.instance.repository
    var templates by remember { mutableStateOf(repo.listTemplates()) }
    var message by remember { mutableStateOf("") }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var name by remember { mutableStateOf("template.png") }
    var left by remember { mutableStateOf("0") }
    var top by remember { mutableStateOf("0") }
    var right by remember { mutableStateOf("200") }
    var bottom by remember { mutableStateOf("200") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Button(onClick = {
                val a11y = FlowAccessibilityService.instance
                if (a11y == null) {
                    message = "Enable Accessibility first"
                    return@Button
                }
                val bmp = a11y.captureBitmap()
                if (bmp == null) {
                    message = "Screenshot failed — grant screen capture on API 29 (Setup)"
                } else {
                    cropBitmap = bmp
                    right = bmp.width.toString()
                    bottom = minOf(200, bmp.height).toString()
                    message = "Screenshot captured (${bmp.width}x${bmp.height}). Set crop and save."
                }
            }, Modifier.fillMaxWidth()) { Text("Capture screenshot for template") }

            if (cropBitmap != null) {
                OutlinedTextField(name, { name = it }, label = { Text("Filename") }, modifier = Modifier.fillMaxWidth())
                Row {
                    OutlinedTextField(left, { left = it }, label = { Text("L") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(top, { top = it }, label = { Text("T") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(right, { right = it }, label = { Text("R") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(bottom, { bottom = it }, label = { Text("B") }, modifier = Modifier.weight(1f))
                }
                Button(onClick = {
                    val src = cropBitmap ?: return@Button
                    val l = left.toIntOrNull() ?: 0
                    val t = top.toIntOrNull() ?: 0
                    val r = (right.toIntOrNull() ?: src.width).coerceAtMost(src.width)
                    val b = (bottom.toIntOrNull() ?: src.height).coerceAtMost(src.height)
                    if (r <= l || b <= t) { message = "Invalid crop"; return@Button }
                    val cropped = Bitmap.createBitmap(src, l, t, r - l, b - t)
                    val out = File(repo.templatesDir(), name.ifBlank { "template.png" })
                    FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    cropped.recycle()
                    templates = repo.listTemplates()
                    message = "Saved ${out.name}"
                }, Modifier.fillMaxWidth()) { Text("Save cropped template") }
            }

            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("Saved templates", style = MaterialTheme.typography.titleSmall)
            LazyColumn {
                items(templates) { n ->
                    val f = File(repo.templatesDir(), n)
                    val bmp = remember(n) { BitmapFactory.decodeFile(f.absolutePath) }
                    ListItem(
                        headlineContent = { Text(n) },
                        supportingContent = { Text("${f.length()} bytes") },
                        leadingContent = {
                            if (bmp != null) {
                                Image(bmp.asImageBitmap(), null, Modifier.size(48.dp))
                            }
                        }
                    )
                }
            }
        }
    }
}
