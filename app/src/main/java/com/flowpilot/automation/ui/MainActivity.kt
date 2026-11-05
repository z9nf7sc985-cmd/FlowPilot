package com.flowpilot.automation.ui

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flowpilot.automation.FlowPilotApp
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
                                onOnboarding = { nav.navigate("onboarding") }
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
                                onAddBlock = { autoId -> nav.navigate("addblock/$autoId") }
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
                            "addblock/{autoId}",
                            arguments = listOf(navArgument("autoId") { type = NavType.StringType })
                        ) { entry ->
                            AddBlockScreen(
                                automationId = entry.arguments!!.getString("autoId")!!,
                                onBack = { nav.popBackStack() }
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
                                    val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    startActivity(i)
                                },
                                onTemplates = { nav.navigate("templates") },
                                onOnboarding = { nav.navigate("onboarding") }
                            )
                        }
                        composable("onboarding") {
                            OnboardingScreen(
                                onDone = { nav.popBackStack() },
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
                                }
                            )
                        }
                        composable("templates") {
                            TemplateScreen(onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }

        if (intent?.getStringExtra("route") == "log") {
            // navigation handled after composition; users can open log from bottom bar
        }
    }
}
