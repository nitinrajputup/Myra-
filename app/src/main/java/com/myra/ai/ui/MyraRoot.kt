package com.myra.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myra.ai.MyraApplication
import com.myra.ai.automation.Automation
import com.myra.ai.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val dests = listOf(
    Dest("home", "Home", Icons.Default.Home),
    Dest("chat", "Chat", Icons.Default.Email),
    Dest("voice", "Voice", Icons.Default.Phone),
    Dest("code", "Code", Icons.Default.Build),
    Dest("auto", "Auto", Icons.Default.Star),
    Dest("memory", "Memory", Icons.Default.Favorite),
    Dest("settings", "Settings", Icons.Default.Settings)
)

@Composable
fun MyraRoot() {
    val vm: MainViewModel = viewModel()
    val voiceVm: VoiceViewModel = viewModel()
    val codeVm: CodeViewModel = viewModel()
    val app = LocalContext.current.applicationContext as MyraApplication
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val pending by Automation.pending.collectAsState()
    val status by Automation.status.collectAsState()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0620), Color.Black)))) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0B0B12)) {
                    dests.forEach { d ->
                        NavigationBarItem(
                            selected = current == d.route,
                            onClick = { nav.navigate(d.route) { popUpTo("home"); launchSingleTop = true } },
                            icon = { Icon(d.icon, d.label) },
                            label = { Text(d.label, fontSize = 9.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White, indicatorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        ) { pad ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
                composable("home") { HomeScreen(vm) { nav.navigate(it) } }
                composable("chat") { ChatScreen(vm) }
                composable("voice") { VoiceScreen(voiceVm) }
                composable("code") { CodeScreen(codeVm) }
                composable("auto") { AutomationScreen(vm) }
                composable("memory") { MemoryScreen(vm) }
                composable("settings") { SettingsScreen(vm) }
            }
        }

        if (status.startsWith("Running")) {
            Button(
                onClick = { Automation.emergencyStop(app) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 12.dp)
            ) { Text("⛔ STOP") }
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { Automation.pending.value = null },
            title = { Text("Confirm action") },
            text = { Text(p.description) },
            confirmButton = {
                TextButton(onClick = {
                    Automation.pending.value = null
                    scope.launch(Dispatchers.Default) { Automation.log(p.run()) }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Automation.pending.value = null
                    Automation.log("Cancelled: ${p.description}")
                }) { Text("Cancel") }
            }
        )
    }
}
