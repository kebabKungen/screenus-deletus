package com.screenusdeletus.enforcement

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.HabitSuggester
import com.screenusdeletus.core.ReflectionPrompts
import com.screenusdeletus.core.TriggerLog
import com.screenusdeletus.ui.components.EmergencyUnlockFlow
import com.screenusdeletus.ui.theme.ScreenusDeletusTheme
import com.screenusdeletus.util.Time
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen block or interstitial shown over a restricted app. It can't be dismissed back into
 * the app: Back and "Return home" go to the launcher. Each visit asks a reflection question and
 * logs the answer as a trigger for later analysis.
 */
class BlockingActivity : ComponentActivity() {

    private val container by lazy { ScreenusDeletusApp.container(this) }
    private var logged = false
    private var renderToken = 0

    private var trigger: String? = null
    private var mood = 0
    private var stress = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logged = false
        render()
    }

    private fun render() {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_BLOCK
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
        val delaySeconds = intent.getIntExtra(EXTRA_DELAY, 0)
        val lockdown = intent.getBooleanExtra(EXTRA_LOCKDOWN, false)
        val label = container.catalog.label(pkg)
        val prefs = container.profile.profile.preferredAlternatives
        val token = ++renderToken

        setContent {
            ScreenusDeletusTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    key(token) { BlockScreen(
                        appLabel = label,
                        mode = mode,
                        reason = reason,
                        delaySeconds = delaySeconds,
                        lockdown = lockdown,
                        preferences = prefs,
                        onReflection = { t, m, s -> trigger = t; mood = m; stress = s },
                        onHome = { goHome() },
                        onProceed = {
                            logTrigger(pkg, proceeded = true)
                            lifecycleScope.launch {
                                container.enforcement.onInterstitialProceeded(pkg, ruleId)
                                finish()
                            }
                        },
                        onUnlocked = {
                            logTrigger(pkg, proceeded = true)
                            finish()
                        },
                        onOpenApp = { p ->
                            packageManager.getLaunchIntentForPackage(p)?.let { startActivity(it); finish() }
                        },
                        isInstalled = { p -> packageManager.getLaunchIntentForPackage(p) != null },
                    ) }
                }
            }
        }
    }

    private fun logTrigger(pkg: String, proceeded: Boolean) {
        if (logged) return
        logged = true
        val now = Time.now()
        val log = TriggerLog(now, pkg, Time.hourOf(now), Time.isoDayOfWeek(now), trigger, mood, stress, proceeded)
        container.scope.launch { container.triggers.log(log) }
    }

    private fun goHome() {
        logTrigger(intent.getStringExtra(EXTRA_PACKAGE).orEmpty(), proceeded = false)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_MODE = "mode"
        const val EXTRA_REASON = "reason"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_DELAY = "delay"
        const val EXTRA_LOCKDOWN = "lockdown"
        const val MODE_BLOCK = "block"
        const val MODE_INTERSTITIAL = "interstitial"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockScreen(
    appLabel: String,
    mode: String,
    reason: String,
    delaySeconds: Int,
    lockdown: Boolean,
    preferences: List<String>,
    onReflection: (String?, Int, Int) -> Unit,
    onHome: () -> Unit,
    onProceed: () -> Unit,
    onUnlocked: () -> Unit,
    onOpenApp: (String) -> Unit,
    isInstalled: (String) -> Boolean,
) {
    val prompt = remember { ReflectionPrompts.pick() }
    val suggestion = remember { HabitSuggester.suggest(15, preferences) }
    var trigger by remember { mutableStateOf<String?>(null) }
    var mood by remember { mutableFloatStateOf(5f) }
    var stress by remember { mutableFloatStateOf(5f) }
    var remaining by remember { mutableIntStateOf(delaySeconds) }
    var showUnlock by remember { mutableStateOf(false) }

    LaunchedEffect(delaySeconds) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showUnlock) {
            EmergencyUnlockFlow { granted -> if (granted) onUnlocked() else showUnlock = false }
            return@Column
        }

        Spacer(Modifier.height(24.dp))
        Text(
            if (mode == BlockingActivity.MODE_INTERSTITIAL) "Pause before $appLabel" else "$appLabel is blocked",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(reason, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (lockdown) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Lockdown mode is on because tampering was detected. Every rule is a hard block for now.",
                    Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(prompt, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReflectionPrompts.commonTriggers.forEach { t ->
                        FilterChip(
                            selected = trigger == t,
                            onClick = {
                                trigger = if (trigger == t) null else t
                                onReflection(trigger, mood.toInt(), stress.toInt())
                            },
                            label = { Text(t) },
                        )
                    }
                }
                Text("Mood: ${mood.toInt()}/10")
                Slider(mood, { mood = it; onReflection(trigger, it.toInt(), stress.toInt()) }, valueRange = 1f..10f, steps = 8)
                Text("Stress: ${stress.toInt()}/10")
                Slider(stress, { stress = it; onReflection(trigger, mood.toInt(), it.toInt()) }, valueRange = 1f..10f, steps = 8)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Try this instead", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("${suggestion.title} · ${suggestion.minutes} min", style = MaterialTheme.typography.titleMedium)
                Text(suggestion.description)
                suggestion.suggestedPackage?.takeIf(isInstalled)?.let { p ->
                    TextButton(onClick = { onOpenApp(p) }) { Text("Open it") }
                }
            }
        }

        Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Return to home") }

        if (mode == BlockingActivity.MODE_INTERSTITIAL) {
            OutlinedButton(onClick = onProceed, enabled = remaining <= 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (remaining > 0) "Continue in ${remaining}s" else "Continue anyway (counts as a slip)")
            }
        } else {
            OutlinedButton(onClick = { showUnlock = true }, modifier = Modifier.fillMaxWidth()) { Text("Request emergency unlock") }
        }
    }
}
