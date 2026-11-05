package com.flowpilot.automation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Welcome to FlowPilot") }) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("FlowPilot is a visual automation builder (like Tasker / MacroDroid / WinAutomation) for personal productivity.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Text("1. Enable Accessibility", style = MaterialTheme.typography.titleMedium)
            Text("Settings → Accessibility → Installed apps → FlowPilot → On. This lets flows tap, swipe, type, and read UI.")
            Button(onClick = onOpenAccessibility, Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Open Accessibility settings")
            }
            Text("2. Allow display over other apps", style = MaterialTheme.typography.titleMedium)
            Text("Needed for the floating control and coordinate picker.")
            Button(onClick = onOpenOverlay, Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Open overlay permission")
            }
            Text("3. Samsung Galaxy S9+ tips", style = MaterialTheme.typography.titleMedium)
            Text("• Device care → Battery → FlowPilot → Unrestricted\n• After reboot, confirm Accessibility is still on (Samsung may disable it)\n• Disable 'Put unused apps to sleep' for FlowPilot")
            Spacer(Modifier.height(12.dp))
            Text("4. Screen capture (API 29)", style = MaterialTheme.typography.titleMedium)
            Text("For Find Image on Android 10, grant screen capture once from Setup. On Android 11+ (API 30) Accessibility screenshots are used instead.")
            Spacer(Modifier.height(12.dp))
            Text("Try the starter examples on the Flows tab. Edit packages/templates before running.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone, Modifier.fillMaxWidth()) { Text("Got it") }
        }
    }
}
