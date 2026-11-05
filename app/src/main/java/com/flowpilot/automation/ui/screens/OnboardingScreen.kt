package com.flowpilot.automation.ui.screens

import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flowpilot.automation.a11y.FlowAccessibilityService
import com.flowpilot.automation.service.ScreenCaptureHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenCapture: () -> Unit = {},
    onOpenBattery: () -> Unit = {},
    onOpenNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val a11yOn = remember(tick) { FlowAccessibilityService.instance != null }
    val overlayOn = remember(tick) { Settings.canDrawOverlays(context) }
    val captureOn = remember(tick) { ScreenCaptureHolder.hasProjection() }
    val batteryOk = remember(tick) {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val notifOk = remember(tick) {
        if (Build.VERSION.SDK_INT < 33) true
        else context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Setup checklist") }) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Turn these on so FlowPilot can tap, swipe, and watch the screen. Status refreshes when you come back.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))

            ChecklistCard(
                title = "Accessibility",
                ok = a11yOn,
                help = "Lets flows tap buttons, type text, and read what’s on screen. Settings → Accessibility → Installed apps → FlowPilot → On.",
                button = "Open Accessibility settings",
                onClick = onOpenAccessibility
            )
            ChecklistCard(
                title = "Display over other apps",
                ok = overlayOn,
                help = "Needed for Pick element, Pick point, Record, and the floating control.",
                button = "Open overlay permission",
                onClick = onOpenOverlay
            )
            ChecklistCard(
                title = "Screen capture",
                ok = captureOn,
                help = "Needed on Android 10 (Galaxy S9+) for Find image / Pick image. Grant once; you’ll see a system prompt.",
                button = "Grant screen capture",
                onClick = onOpenCapture
            )
            ChecklistCard(
                title = "Battery optimization",
                ok = batteryOk,
                help = "Set FlowPilot to Unrestricted so schedules and triggers keep working. On Samsung: Device care → Battery → FlowPilot → Unrestricted.",
                button = "Open battery settings",
                onClick = onOpenBattery
            )
            ChecklistCard(
                title = "Notifications",
                ok = notifOk,
                help = "So you can see when a flow is running and receive flow notifications.",
                button = "Open notification settings",
                onClick = onOpenNotifications
            )

            Spacer(Modifier.height(16.dp))
            Text("Samsung Galaxy S9+ tips", style = MaterialTheme.typography.titleMedium)
            Text(
                "• After reboot, confirm Accessibility is still on\n" +
                    "• Disable “Put unused apps to sleep” for FlowPilot\n" +
                    "• Prefer Unrestricted battery, not Optimized",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onDone, Modifier.fillMaxWidth()) { Text("Done — open Templates") }
            TextButton(onClick = onDone, Modifier.fillMaxWidth()) { Text("Skip for now") }
        }
    }
}

@Composable
private fun ChecklistCard(
    title: String,
    ok: Boolean,
    help: String,
    button: String,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(if (ok) "Ready" else "Needs attention", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(help, style = MaterialTheme.typography.bodySmall)
            if (!ok) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClick, Modifier.fillMaxWidth()) { Text(button) }
            } else {
                TextButton(onClick = onClick, Modifier.fillMaxWidth()) { Text("Open settings again") }
            }
        }
    }
}
