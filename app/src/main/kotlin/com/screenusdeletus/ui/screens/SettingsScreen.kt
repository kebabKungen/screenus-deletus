package com.screenusdeletus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.security.AuthManager
import com.screenusdeletus.ui.components.PinField
import com.screenusdeletus.ui.components.SectionCard
import com.screenusdeletus.util.Time

@Composable
fun SettingsScreen() {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    var message by remember { mutableStateOf<String?>(null) }

    var goal by remember { mutableFloatStateOf(c.profile.dailyGoalMs / 3_600_000f) }

    val s = remember { c.unlocks.settings }
    var maxUnlocks by remember { mutableFloatStateOf(s.maxUnlocksPerWeek.toFloat()) }
    var cooldownH by remember { mutableFloatStateOf(s.cooldownMs / 3_600_000f) }
    var waitMin by remember { mutableFloatStateOf(s.baseWaitMs / 60_000f) }
    var durationMin by remember { mutableFloatStateOf(s.unlockDurationMs / 60_000f) }
    var challenge by remember { mutableStateOf(s.requiresChallenge) }

    var coolingH by remember { mutableFloatStateOf(c.rules.weakeningDelayMs / 3_600_000f) }
    var pauseMin by remember { mutableFloatStateOf(c.tamper.disarmDelayMs / 60_000f) }

    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf(c.auth.biometricEnabled) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        SectionCard("Daily goal") {
            Text(formatDuration((goal * 3_600_000).toLong()))
            Slider(goal, { goal = it }, valueRange = 0.5f..8f, steps = 14)
            Button(onClick = { c.profile.dailyGoalMs = (goal * 3_600_000).toLong(); message = "Goal saved" }) { Text("Save goal") }
        }

        SectionCard("Emergency unlocks") {
            Text("Per week: ${maxUnlocks.toInt()}")
            Slider(maxUnlocks, { maxUnlocks = it }, valueRange = 0f..7f, steps = 6)
            Text("Cooldown between unlocks: ${cooldownH.toInt()}h")
            Slider(cooldownH, { cooldownH = it }, valueRange = 0f..24f, steps = 23)
            Text("Wait before the first unlock: ${waitMin.toInt()} min (doubles each time)")
            Slider(waitMin, { waitMin = it }, valueRange = 0f..15f, steps = 14)
            Text("Unlock lasts: ${durationMin.toInt()} min")
            Slider(durationMin, { durationMin = it }, valueRange = 5f..60f, steps = 10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Typing challenge", Modifier.weight(1f))
                Switch(challenge, { challenge = it })
            }
            c.unlocks.pendingSettings?.let { (_, at) -> Text("A looser change is waiting until ${Time.formatDateTime(at)}.", style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                val updated = s.copy(
                    maxUnlocksPerWeek = maxUnlocks.toInt(),
                    cooldownMs = (cooldownH * 3_600_000).toLong(),
                    baseWaitMs = (waitMin * 60_000).toLong(),
                    unlockDurationMs = (durationMin * 60_000).toLong(),
                    requiresChallenge = challenge,
                )
                val at = c.unlocks.updateSettings(updated)
                message = if (at <= Time.now() + 1_000) "Unlock rules saved" else "Looser unlock rules apply from ${Time.formatDateTime(at)}"
            }) { Text("Save unlock rules") }
            Text("Tighter rules apply now; looser ones from next week.", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("Delays") {
            Text("Cooling-off for loosening a rule: ${coolingH.toInt()}h (can only go up)")
            Slider(coolingH, { coolingH = it.coerceAtLeast(c.rules.weakeningDelayMs / 3_600_000f) }, valueRange = 1f..72f, steps = 70)
            Text("Wait before a protection pause starts: ${pauseMin.toInt()} min (can only go up)")
            Slider(pauseMin, { pauseMin = it.coerceAtLeast(c.tamper.disarmDelayMs / 60_000f) }, valueRange = 5f..240f, steps = 46)
            Button(onClick = {
                c.rules.setWeakeningDelay((coolingH * 3_600_000).toLong())
                c.tamper.setDisarmDelay((pauseMin * 60_000).toLong())
                message = "Delays saved"
            }) { Text("Save delays") }
        }

        SectionCard("PIN & biometrics") {
            PinField(oldPin, { oldPin = it }, "Current PIN")
            PinField(newPin, { newPin = it }, "New PIN")
            OutlinedButton(enabled = newPin.length >= AuthManager.MIN_PIN_LENGTH, onClick = {
                message = when (val r = c.auth.verifyPin(oldPin)) {
                    AuthManager.PinResult.Ok -> { c.auth.setPin(newPin); "PIN changed" }
                    is AuthManager.PinResult.Wrong -> "Current PIN is wrong"
                    is AuthManager.PinResult.LockedOut -> "Locked until ${Time.formatClock(r.until)}"
                }
                oldPin = ""; newPin = ""
            }) { Text("Change PIN") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Unlock with biometrics", Modifier.weight(1f))
                Switch(bio, { bio = it; c.auth.biometricEnabled = it })
            }
        }
    }
}

