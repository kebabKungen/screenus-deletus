package com.screenusdeletus.ui.screens

import com.screenusdeletus.ui.components.ValueSlider
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.screenusdeletus.ui.components.LogoMark
import com.screenusdeletus.ui.components.ScreenHeader
import com.screenusdeletus.ui.components.StepDots
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.AppCategory
import com.screenusdeletus.core.EscalationLevel
import com.screenusdeletus.core.HabitSuggester
import com.screenusdeletus.core.ScreenTimeRule
import com.screenusdeletus.core.TimeRange
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.data.repo.BehaviorProfile
import com.screenusdeletus.data.repo.RulesRepository
import com.screenusdeletus.monitoring.GuardService
import com.screenusdeletus.security.AuthManager
import com.screenusdeletus.ui.components.PinField
import com.screenusdeletus.ui.components.StatusRow
import com.screenusdeletus.util.Permissions
import com.screenusdeletus.util.Time
import kotlinx.coroutines.launch

private const val STEPS = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(activity: FragmentActivity, onFinished: () -> Unit) {
    val context = LocalContext.current
    val c = ScreenusDeletusApp.container(context)
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }

    // Questionnaire
    var estimate by remember { mutableFloatStateOf(4f) }
    var goal by remember { mutableFloatStateOf(2f) }
    var categories by remember { mutableStateOf(setOf(AppCategory.SOCIAL_MEDIA, AppCategory.ENTERTAINMENT)) }
    var times by remember { mutableStateOf(setOf<String>()) }
    var alternatives by remember { mutableStateOf(setOf<String>()) }
    var motivation by remember { mutableStateOf("") }

    // PIN
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf(c.auth.canUseBiometrics(activity)) }

    // First rule
    var limitMinutes by remember { mutableFloatStateOf(30f) }
    var nightBlock by remember { mutableStateOf(true) }

    // Permissions, refreshed whenever we come back from a settings screen
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    // Services bind asynchronously after being switched on, so poll while on the permissions step.
    LaunchedEffect(step) {
        while (step == 3) {
            kotlinx.coroutines.delay(1_000)
            refresh++
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StepDots(STEPS, step)
            when (step) {
                0 -> {
                    Spacer(Modifier.height(12.dp))
                    LogoMark(size = 88.dp)
                    Text("Screenus Deletus", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "A screen time tool for people who know every way around a screen time tool.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FeatureRow(Icons.Filled.Timer, "Friction, not walls", "Pauses that grow each time, hard blocks when you need them, and 24 h before any rule can be loosened.")
                    FeatureRow(Icons.Filled.Shield, "Costly to bypass", "Turning protections off triggers lockdown, breaks your streak and is logged.")
                    FeatureRow(Icons.Filled.People, "Someone in your corner", "An optional guardian approves unlocks and hears about tamper attempts.")
                    FeatureRow(Icons.Filled.PhonelinkLock, "Private by design", "Everything stays encrypted on this device. No accounts, no servers.")
                }
                1 -> {
                    ScreenHeader("About your habits", "Honest answers make better defaults.", eyebrow = "Step 2 of $STEPS")
                    ValueSlider("Phone use on a typical day", "${estimate.toInt()}h", estimate, { estimate = it }, 1f..12f)
                    ValueSlider("Your daily goal", formatDuration((goal * 3_600_000).toLong()), goal, { goal = it }, 0.5f..8f, step = 0.5f)
                    Text("Which kinds of apps pull you in?", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppCategory.entries.filter { it != AppCategory.OTHER && it != AppCategory.UTILITIES }.forEach { cat ->
                            FilterChip(cat in categories, { categories = categories.toggle(cat) }, { Text(cat.label) })
                        }
                    }
                    Text("When is it worst?", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Morning in bed", "Work hours", "Evenings", "Late night", "Weekends").forEach { t ->
                            FilterChip(t in times, { times = times.toggle(t) }, { Text(t) })
                        }
                    }
                    Text("What would you rather do instead?", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HabitSuggester.tags.forEach { t ->
                            FilterChip(t in alternatives, { alternatives = alternatives.toggle(t) }, { Text(t) })
                        }
                    }
                    OutlinedTextField(motivation, { motivation = it }, Modifier.fillMaxWidth(), label = { Text("Why does this matter to you?") }, minLines = 2)
                }
                2 -> {
                    ScreenHeader("Create a PIN", "Protects your settings. Pick something you won't type on autopilot.", eyebrow = "Step 3 of $STEPS")
                    PinField(pin, { pin = it }, "PIN (at least ${AuthManager.MIN_PIN_LENGTH} digits)")
                    PinField(pin2, { pin2 = it }, "Repeat PIN", isError = pin2.isNotEmpty() && pin2 != pin)
                    if (c.auth.canUseBiometrics(activity)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Unlock with fingerprint or face", Modifier.weight(1f))
                            Switch(bio, { bio = it })
                        }
                    }
                }
                3 -> {
                    refresh // read so the rows recompose on resume
                    ScreenHeader("Permissions", "Usage access and the accessibility service are required. The rest make bypassing harder.", eyebrow = "Step 4 of $STEPS")
                    StatusRow("Usage access", c.usage.hasUsageAccess(), "Measures time per app") { Permissions.usageAccess(context) }
                    StatusRow("Accessibility service", c.tamper.isAccessibilityEnabled(), "Notices app switches instantly. Reads only the app's package name.") { Permissions.accessibility(context) }
                    StatusRow("Device admin", c.tamper.isDeviceAdminActive(), "Blocks one-tap uninstalling") { Permissions.deviceAdmin(context) }
                    val notifOk = Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    StatusRow("Notifications", notifOk, "Warnings and alerts") {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    StatusRow("Display over other apps", c.tamper.hasOverlayPermission(), "Backup blocking if accessibility is off") { Permissions.overlay(context) }
                    StatusRow("Ignore battery optimisation", c.tamper.isIgnoringBatteryOptimizations(), "Keeps protection running") { Permissions.batteryOptimizations(context) }
                }
                4 -> {
                    ScreenHeader("Your first rule", eyebrow = "Step 5 of $STEPS")
                    Text("Applies to: " + categories.joinToString { it.label }.ifEmpty { "nothing selected — go back and pick categories" })
                    ValueSlider("Daily limit", formatDuration((limitMinutes * 60_000).toLong()), limitMinutes, { limitMinutes = it }, 0f..180f, step = 5f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Block them 22:00–07:00 too", Modifier.weight(1f))
                        Switch(nightBlock, { nightBlock = it })
                    }
                    Text(
                        "Starts as a 'pause' rule: once you're over, you'll wait through a growing delay before the app opens. " +
                            "You can make it a hard block later. Loosening any rule takes 24 hours to apply.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                5 -> {
                    Spacer(Modifier.height(12.dp))
                    LogoMark(size = 72.dp)
                    ScreenHeader("You're set", "Your future self says thanks.", eyebrow = "Step 6 of $STEPS")
                    FeatureRow(Icons.Filled.People, "Add a guardian", "More → Guardian. Accountability works better with a real person.")
                    FeatureRow(Icons.Filled.Savings, "Make a pledge", "More → Commitment pledge, if money motivates you.")
                    FeatureRow(Icons.Filled.Insights, "Check Insights in a few days", "Your triggers and heaviest hours show up there.")
                }
            }

            Spacer(Modifier.padding(4.dp))
            val canContinue = when (step) {
                2 -> pin.length >= AuthManager.MIN_PIN_LENGTH && pin == pin2
                3 -> {
                    refresh
                    c.usage.hasUsageAccess() && c.tamper.isAccessibilityEnabled()
                }
                else -> true
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (step > 0) OutlinedButton(onClick = { step-- }, shape = RoundedCornerShape(18.dp), modifier = Modifier.height(52.dp)) { Text("Back") }
                Button(
                    enabled = canContinue,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = {
                        when (step) {
                            2 -> {
                                c.auth.setPin(pin)
                                c.auth.biometricEnabled = bio
                                step++
                            }
                            STEPS - 1 -> scope.launch {
                                c.profile.profile = BehaviorProfile(
                                    estimatedDailyMinutes = (estimate * 60).toInt(),
                                    goalDailyMinutes = (goal * 60).toInt(),
                                    problemCategories = categories.map { it.name },
                                    worstTimes = times.toList(),
                                    preferredAlternatives = alternatives.toList(),
                                    motivation = motivation,
                                )
                                c.profile.dailyGoalMs = (goal * 3_600_000).toLong()
                                if (categories.isNotEmpty()) {
                                    c.rules.save(
                                        ScreenTimeRule(
                                            id = RulesRepository.newId(),
                                            name = "Distracting apps",
                                            targetPackages = emptySet(),
                                            targetCategories = categories,
                                            dailyLimitMs = (limitMinutes * 60_000).toLong(),
                                            blockedHours = if (nightBlock) listOf(TimeRange.parse("22:00-07:00")) else emptyList(),
                                            escalationLevel = EscalationLevel.MEDIUM,
                                            createdAt = Time.now(),
                                        ),
                                    )
                                }
                                c.tamper.check(forceIntegrity = true)
                                c.profile.onboardingComplete = true
                                GuardService.start(context)
                                onFinished()
                            }
                            else -> step++
                        }
                    },
                ) { Text(if (step == STEPS - 1) "Start" else "Continue") }
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
