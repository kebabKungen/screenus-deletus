package com.screenusdeletus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.UnlockChallenge
import com.screenusdeletus.core.UnlockEligibility
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.data.repo.ViolationRecorder
import com.screenusdeletus.security.AuthManager
import com.screenusdeletus.util.Time
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Step {
    data object Reason : Step
    data class Denied(val reason: String, val retryAt: Long?) : Step
    data class Waiting(val until: Long, val eligibility: UnlockEligibility.Eligible) : Step
    data class Challenge(val phrase: String, val eligibility: UnlockEligibility.Eligible) : Step
    data object Guardian : Step
    data class Granted(val until: Long) : Step
}

/**
 * Emergency unlock: explain why → wait (leaving the screen restarts the wait) → type a commitment
 * phrase → guardian PIN if required → temporary unlock. Every unlock counts as a violation and is
 * reported to the guardian.
 */
@Composable
fun EmergencyUnlockFlow(onDone: (granted: Boolean) -> Unit) {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<Step>(Step.Reason) }
    var reason by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(Time.now()) }

    // Leaving the screen during the wait resets the flow — no "start the timer and come back".
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && step is Step.Waiting) step = Step.Reason
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val grant: (String) -> Unit = { by ->
        scope.launch {
            val until = c.unlocks.grant(reason.trim(), by)
            c.violations.record(null, ViolationRecorder.EMERGENCY_UNLOCK, oncePerDay = false)
            c.guardian.notify(
                "Emergency unlock used",
                "Reason: ${reason.trim()}\nApproved by: $by. Unlocked for ${formatDuration(until - Time.now())}.",
            )
            step = Step.Granted(until)
        }
    }

    fun afterChallenge(el: UnlockEligibility.Eligible) {
        if (el.requiresGuardian) step = Step.Guardian else grant("self")
    }

    LaunchedEffect(step) {
        val s = step
        if (s is Step.Waiting) {
            while (Time.now() < s.until) {
                now = Time.now()
                delay(250)
            }
            if (s.eligibility.requiresChallenge) {
                step = Step.Challenge(UnlockChallenge.generate(), s.eligibility)
            } else {
                afterChallenge(s.eligibility)
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val s = step) {
            Step.Reason -> {
                Text("Emergency unlock", style = MaterialTheme.typography.titleLarge)
                Text("Why do you need this right now? Be specific — this is logged and shared with your guardian.")
                OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Reason") })
                Text("${reason.trim().length}/$MIN_REASON characters", style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = reason.trim().length >= MIN_REASON,
                    onClick = {
                        scope.launch {
                            when (val el = c.unlocks.eligibility(c.guardian.isConfigured, c.tamper.protectionLevel)) {
                                is UnlockEligibility.Denied -> step = Step.Denied(el.reason, el.retryAt)
                                is UnlockEligibility.Eligible -> step = Step.Waiting(Time.now() + el.waitMs, el)
                            }
                        }
                    },
                ) { Text("Continue") }
                OutlinedButton(onClick = { scope.launch { c.unlocks.logAbandoned(reason); onDone(false) } }) { Text("Never mind") }
            }
            is Step.Denied -> {
                Text("Unlock not available", style = MaterialTheme.typography.titleLarge)
                Text(s.reason)
                s.retryAt?.let { Text("Available again ${Time.formatDateTime(it)}.") }
                Button(onClick = { onDone(false) }) { Text("OK") }
            }
            is Step.Waiting -> {
                Text("Take a moment", style = MaterialTheme.typography.titleLarge)
                Text("Stay on this screen. If you still need the unlock when the timer ends, you can continue. Leaving restarts the wait.")
                val total = s.eligibility.waitMs.coerceAtLeast(1)
                LinearProgressIndicator(progress = { 1f - (s.until - now).toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Text(Time.formatCountdown(s.until - now), style = MaterialTheme.typography.displaySmall)
                OutlinedButton(onClick = { scope.launch { c.unlocks.logAbandoned(reason); onDone(false) } }) { Text("I don't need it after all") }
            }
            is Step.Challenge -> {
                Text("Type this exactly", style = MaterialTheme.typography.titleLarge)
                Text(s.phrase, style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(typed, { typed = it; error = null }, Modifier.fillMaxWidth(), isError = error != null,
                    supportingText = { error?.let { Text(it) } })
                Button(onClick = {
                    if (UnlockChallenge.verify(s.phrase, typed)) afterChallenge(s.eligibility) else error = "Doesn't match"
                }) { Text("Confirm") }
            }
            Step.Guardian -> {
                val name = c.guardian.guardian?.name ?: "your guardian"
                Text("Guardian approval", style = MaterialTheme.typography.titleLarge)
                Text("Hand the phone to $name, or call them, to enter their PIN.")
                PinField(pin, { pin = it; error = null }, "Guardian PIN", isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    when (val r = c.auth.verifyGuardianPin(pin)) {
                        AuthManager.PinResult.Ok -> grant("guardian")
                        is AuthManager.PinResult.Wrong -> error = "Wrong PIN"
                        is AuthManager.PinResult.LockedOut -> error = "Too many attempts. Try again at ${Time.formatClock(r.until)}."
                    }
                }) { Text("Approve") }
            }
            is Step.Granted -> {
                Text("Unlocked until ${Time.formatClock(s.until)}", style = MaterialTheme.typography.titleLarge)
                Text("Your rules are back on after that. This unlock was logged.")
                Button(onClick = { onDone(true) }) { Text("Done") }
            }
        }
    }

}

private const val MIN_REASON = 20
