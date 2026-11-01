package com.screenusdeletus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.fragment.app.FragmentActivity
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.ui.screens.AnalyticsScreen
import com.screenusdeletus.ui.screens.CommitmentScreen
import com.screenusdeletus.ui.screens.DashboardScreen
import com.screenusdeletus.ui.screens.GuardianScreen
import com.screenusdeletus.ui.screens.LockScreen
import com.screenusdeletus.ui.screens.MoreScreen
import com.screenusdeletus.ui.screens.OnboardingScreen
import com.screenusdeletus.ui.screens.RuleEditorScreen
import com.screenusdeletus.ui.screens.RulesScreen
import com.screenusdeletus.ui.screens.SecurityScreen
import com.screenusdeletus.ui.screens.SettingsScreen
import com.screenusdeletus.ui.screens.UnlockScreen
import com.screenusdeletus.util.Time

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Today", Icons.Filled.Home),
    Tab("rules", "Rules", Icons.Filled.List),
    Tab("insights", "Insights", Icons.Filled.BarChart),
    Tab("security", "Security", Icons.Filled.Shield),
    Tab("more", "More", Icons.Filled.MoreHoriz),
)

@Composable
fun AppRoot(activity: FragmentActivity, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    var onboarded by remember { mutableStateOf(c.profile.onboardingComplete) }
    // Stats are always viewable; only the settings area (the More tab) is behind the PIN.
    var settingsLocked by rememberSaveable { mutableStateOf(true) }
    var stoppedAt by remember { mutableLongStateOf(0L) }

    // Re-lock after the app has been in the background for a minute.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> stoppedAt = Time.now()
                Lifecycle.Event.ON_START -> if (stoppedAt > 0 && Time.now() - stoppedAt > RELOCK_MS) settingsLocked = true
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    when {
        !onboarded -> OnboardingScreen(activity) {
            onboarded = true
            settingsLocked = false
        }
        else -> MainNav(activity, settingsLocked, { settingsLocked = false }, pendingRoute, onRouteConsumed)
    }
}

@Composable
private fun MainNav(
    activity: FragmentActivity,
    settingsLocked: Boolean,
    onUnlockSettings: () -> Unit,
    pendingRoute: String?,
    onRouteConsumed: () -> Unit,
) {
    /** Shows [content] only once the PIN or biometrics have been entered. */
    @Composable
    fun Gated(content: @Composable () -> Unit) {
        if (settingsLocked) LockScreen(activity, fullScreen = false, onUnlocked = onUnlockSettings) else content()
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            nav.navigate(pendingRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            // Always land on the tab's own screen (e.g. More, not the Guardian page
                            // that was last open under it).
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(padding)) {
            composable("dashboard") { DashboardScreen(onNavigate = { nav.navigate(it) }) }
            composable("rules") { RulesScreen(onEdit = { id -> nav.navigate("rule/$id") }) }
            composable("rule/{id}") { entry ->
                RuleEditorScreen(id = entry.arguments?.getString("id") ?: "new", onDone = { nav.popBackStack() })
            }
            composable("insights") { AnalyticsScreen() }
            composable("security") { SecurityScreen() }
            composable("more") { Gated { MoreScreen(onNavigate = { nav.navigate(it) }) } }
            composable("guardian") { Gated { GuardianScreen() } }
            composable("commitment") { Gated { CommitmentScreen() } }
            composable("settings") { Gated { SettingsScreen() } }
            composable("unlock") { Gated { UnlockScreen(onDone = { nav.popBackStack() }) } }
        }
    }
}

private const val RELOCK_MS = 60_000L
