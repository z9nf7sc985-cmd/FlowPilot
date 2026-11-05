package com.flowpilot.automation.ui.screens

import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.a11y.FlowAccessibilityService
import com.flowpilot.automation.overlay.CoordPickerService
import com.flowpilot.automation.overlay.ElementPickerService
import com.flowpilot.automation.overlay.ImageCropPickerService
import com.flowpilot.automation.overlay.OverlayControlService
import com.flowpilot.automation.overlay.RecordModeService
import com.flowpilot.automation.overlay.SwipeRecorderService
import com.flowpilot.automation.service.ScreenCaptureHolder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestCapture: () -> Unit,
    onRequestBattery: () -> Unit,
    onTemplates: () -> Unit,
    onGallery: () -> Unit = {},
    onOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val a11yOn = remember(tick) { FlowAccessibilityService.instance != null }
    val overlayOn = remember(tick) { Settings.canDrawOverlays(context) }
    val captureOn = remember(tick) { ScreenCaptureHolder.hasProjection() }
    val scope = rememberCoroutineScope()
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Setup & tools") }) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Button(onClick = onOnboarding, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Open setup checklist")
            }
            Button(onClick = onGallery, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Templates gallery")
            }
            StatusRow("Accessibility service", a11yOn, onRequestAccessibility)
            StatusRow("Display over other apps", overlayOn, onRequestOverlay)
            StatusRow("Screen capture (API 29 images)", captureOn, onRequestCapture)
            Button(onClick = onRequestBattery, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Battery optimization (set Unrestricted)")
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Pick-from-screen tools", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { ElementPickerService.start(context) }, Modifier.fillMaxWidth()) {
                Text("Pick element")
            }
            Button(onClick = { CoordPickerService.start(context) }, Modifier.fillMaxWidth()) {
                Text("Pick point")
            }
            Button(onClick = { ImageCropPickerService.start(context) }, Modifier.fillMaxWidth()) {
                Text("Pick image (crop template)")
            }
            Button(onClick = { SwipeRecorderService.start(context) }, Modifier.fillMaxWidth()) {
                Text("Record swipe")
            }
            Button(
                onClick = {
                    if (RecordModeService.isRecording) RecordModeService.stop(context)
                    else RecordModeService.start(context)
                },
                Modifier.fillMaxWidth()
            ) {
                Text(if (RecordModeService.isRecording) "Stop record mode" else "Start record mode")
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Overlays", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { OverlayControlService.start(context) }, Modifier.fillMaxWidth()) {
                Text("Show floating start/stop control")
            }
            Button(onClick = { OverlayControlService.stop(context) }, Modifier.fillMaxWidth()) {
                Text("Hide floating control")
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Button(onClick = onTemplates, Modifier.fillMaxWidth()) {
                Text("Image templates library")
            }
            Button(onClick = { showImport = true }, Modifier.fillMaxWidth()) {
                Text("Import automation JSON")
            }
            if (showImport) {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    label = { Text("Paste JSON") }
                )
                Button(onClick = {
                    scope.launch {
                        runCatching { FlowPilotApp.instance.repository.importJson(importText) }
                        showImport = false
                        importText = ""
                    }
                }) { Text("Import") }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                "Disclaimer: FlowPilot is for personal productivity automation. Follow the terms of service of any apps you automate. Do not use it to cheat in games or evade detection.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label)
                Text(if (ok) "Enabled" else "Not enabled", style = MaterialTheme.typography.labelSmall)
            }
            if (!ok) TextButton(onClick = onFix) { Text("Enable") }
        }
    }
}
