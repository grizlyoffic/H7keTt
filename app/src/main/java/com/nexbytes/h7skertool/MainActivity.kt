package com.nexbytes.h7skertool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.*
import androidx.navigation.compose.*
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.ui.screens.*
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.viewmodel.AppUiState
import com.nexbytes.h7skertool.viewmodel.CaptureViewModel

class MainActivity : ComponentActivity() {
    private val vm: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            H7skERTheme {
                val state by vm.state.collectAsState()
                AppRouter(state, vm)
            }
        }
    }
}

@Composable
private fun AppRouter(state: AppUiState, vm: CaptureViewModel) {
    AnimatedContent(
        targetState = when {
            state.needsShizuku -> "shizuku"
            state.needsPassword -> "password"
            state.needsClientUrl -> "client_url"
            else -> "main"
        },
        transitionSpec = {
            fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
            fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
        },
        label = "route"
    ) { route ->
        when (route) {
            "shizuku" -> ShizukuCheckScreen(
                shizukuAvailable = state.shizukuAvailable,
                permissionGranted = state.shizukuPermissionGranted,
                onRequestPermission = vm::requestShizukuPermission,
                onRetry = vm::checkShizuku
            )
            "password" -> PasswordScreen(
                isVerifying = state.isVerifying,
                error = state.verifyError,
                onVerify = vm::verifyPassword
            )
            "client_url" -> ClientUrlScreen(
                currentUrl = state.clientUrl,
                onContinue = vm::setClientUrl
            )
            "main" -> MainApp(state, vm)
        }
    }
}

@Composable
private fun MainApp(state: AppUiState, vm: CaptureViewModel) {
    val navController = rememberNavController()

    // Resolve back-stack entry for tracking current route
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // Requests map for detail lookup
    val requestMap = remember(state.requests) { state.requests.associateBy { it.id } }

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf("capture", "settings")) {
                NavigationBar(containerColor = CardBlack, tonalElevation = 0.dp) {
                    BottomNavItem.items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonGreen,
                                selectedTextColor = NeonGreen,
                                indicatorColor = NeonGreen.copy(0.1f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        },
        containerColor = DeepBlack
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "capture",
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable("capture") {
                MainCaptureScreen(
                    state = state,
                    onStartCapture = vm::startCapture,
                    onStopCapture = vm::stopCapture,
                    onSearch = vm::setSearch,
                    onFilterEndpoint = vm::setEndpointFilter,
                    onClearCaptures = vm::clearCaptures,
                    onSaveMod = vm::saveModification,
                    onClearLogs = { /* clear logs via vm */ },
                    onNavigateToDetail = { req ->
                        navController.navigate("detail/${req.id}")
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    onChangeClientUrl = {
                        navController.navigate("change_url")
                    },
                    onClearCaptures = vm::clearCaptures,
                    onClearMods = vm::clearModifications,
                    onLogout = vm::logout,
                    onResetAll = vm::resetAll
                )
            }
            composable(
                "detail/{requestId}",
                arguments = listOf(navArgument("requestId") { type = NavType.StringType })
            ) { backStack ->
                val reqId = backStack.arguments?.getString("requestId") ?: return@composable
                val req = requestMap[reqId] ?: return@composable
                RequestDetailScreen(
                    request = req,
                    response = state.responses[req.id],
                    onBack = { navController.popBackStack() },
                    onSaveMod = vm::saveModification
                )
            }
            composable("change_url") {
                ClientUrlScreen(
                    currentUrl = state.clientUrl,
                    onContinue = { url -> vm.setClientUrl(url); navController.popBackStack() }
                )
            }
        }
    }
}

private sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Capture  : BottomNavItem("capture",  "CAPTURE",  Icons.Default.CenterFocusStrong)
    object Settings : BottomNavItem("settings", "SETTINGS", Icons.Default.Settings)
    companion object { val items = listOf(Capture, Settings) }
}
