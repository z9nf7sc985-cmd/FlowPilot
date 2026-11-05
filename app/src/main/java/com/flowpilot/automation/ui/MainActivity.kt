package com.flowpilot.automation.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flowpilot.automation.service.AutomationService
import com.flowpilot.automation.service.ScreenCaptureHolder
import com.flowpilot.automation.triggers.AppOpenWatcher
import com.flowpilot.automation.triggers.ScheduleHelper
import com.flowpilot.automation.ui.screens.*
import com.flowpilot.automation.ui.theme.FlowPilotTheme

class MainActivity : ComponentActivity() {

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            ScreenCaptureHolder.start(this, result.resultCode, result.data!!)
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* checklist refreshes on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppOpenWatcher.install()
        ScheduleHelper.rescheduleAll(this)

        val runId = intent?.getStringExtra("automation_id")
        if (intent?.action == "com.flowpilot.automation.RUN_AUTOMATION" && runId != null) {
            AutomationService.start(this, runId)
        }

        setContent {
            FlowPilotTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route ?: "home"
                val showBottom = route in listOf("home", "log", "settings")

                Scaffold(
                    bottomBar = {
                        if (showBottom) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = route == "home",
                                    onClick = { nav.navigate("home") { popUpTo("home") } },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    label = { Text("Flows") }
                                )
                                NavigationBarItem(
                                    selected = route == "log",
                                    onClick = { nav.navigate("log") },
                                    icon = { Icon(Icons.Default.List, null) },
                                    label = { Text("Run log") }
                                )
                                NavigationBarItem(
                                    selected = route == "settings",
                                    onClick = { nav.navigate("settings") },
                                    icon = { Icon(Icons.Default.Settings, null) },
                                    label = { Text("Setup") }
                                )
                            }
                        }
                    }
                ) { pad ->
                    NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(pad)) {
                        composable("home") {
                            HomeScreen(
                                onOpen = { id -> nav.navigate("editor/$id") },
                                onNew = { nav.navigate("editor/new") },
                                onOnboarding = { nav.navigate("onboarding") },
                                onTemplates = { nav.navigate("gallery") }
                            )
                        }
                        composable(
                            "editor/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("id") ?: "new"
                            EditorScreen(
                                automationId = id,
                                onBack = { nav.popBackStack() },
                                onEditBlock = { autoId, blockId ->
                                    nav.navigate("block/$autoId/$blockId")
                                },
                                onAddBlock = { autoId, parentId, branch ->
                                    val p = parentId ?: "_"
                                    nav.navigate("addblock/$autoId/$p/$branch")
                                }
                            )
                        }
                        composable(
                            "block/{autoId}/{blockId}",
                            arguments = listOf(
                                navArgument("autoId") { type = NavType.StringType },
                                navArgument("blockId") { type = NavType.StringType }
                            )
                        ) { entry ->
                            BlockEditScreen(
                                automationId = entry.arguments!!.getString("autoId")!!,
                                blockId = entry.arguments!!.getString("blockId")!!,
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable(
                            "addblock/{autoId}/{parentId}/{branch}",
                            arguments = listOf(
                                navArgument("autoId") { type = NavType.StringType },
                                navArgument("parentId") { type = NavType.StringType },
                                navArgument("branch") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val parent = entry.arguments!!.getString("parentId")!!
                            AddBlockScreen(
                                automationId = entry.arguments!!.getString("autoId")!!,
                                onBack = { nav.popBackStack() },
                                parentBlockId = parent.takeIf { it != "_" },
                                branch = entry.arguments!!.getString("branch") ?: "then"
                            )
                        }
                        composable("log") { RunLogScreen() }
                        composable("settings") {
                            SettingsScreen(
                                onRequestOverlay = {
                                    val i = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                    startActivity(i)
                                },
                                onRequestAccessibility = {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                onRequestCapture = {
                                    mediaProjectionLauncher.launch(ScreenCaptureHolder.createPermissionIntent(this@MainActivity))
                                },
                                onRequestBattery = {
                                    val pm = getSystemService(PowerManager::class.java)
                                    if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                                        try {
                                            startActivity(
                                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                                    .setData(Uri.parse("package:$packageName"))
                                            )
                                        } catch (_: Exception) {
                                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        }
                                    } else {
                                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                },
                                onTemplates = { nav.navigate("templates") },
                                onGallery = { nav.navigate("gallery") },
                                onOnboarding = { nav.navigate("onboarding") }
                            )
                        }
                        composable("onboarding") {
                            OnboardingScreen(
                                onDone = {
                                    nav.popBackStack()
                                    nav.navigate("gallery")
                                },
                                onOpenAccessibility = {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                onOpenOverlay = {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                },
                                onOpenCapture = {
                                    mediaProjectionLauncher.launch(ScreenCaptureHolder.createPermissionIntent(this@MainActivity))
                                },
                                onOpenBattery = {
                                    val pm = getSystemService(PowerManager::class.java)
                                    if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                                        try {
                                            startActivity(
                                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                                    .setData(Uri.parse("package:$packageName"))
                                            )
                                        } catch (_: Exception) {
                                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        }
                                    } else {
                                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                },
                                onOpenNotifications = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        if (ContextCompat.checkSelfPermission(
                                                this@MainActivity,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                            })
                                        }
                                    } else {
                                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        })
                                    }
                                }
                            )
                        }
                        composable("templates") {
                            TemplateScreen(onBack = { nav.popBackStack() })
                        }
                        composable("gallery") {
                            TemplatesGalleryScreen(
                                onBack = { nav.popBackStack() },
                                onOpened = { id ->
                                    nav.popBackStack()
                                    nav.navigate("editor/$id")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
