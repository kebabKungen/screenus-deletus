package com.screenusdeletus.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.Icons
import com.screenusdeletus.ui.components.ScreenHeader
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.ProtectionLevel
import com.screenusdeletus.core.TamperType
import com.screenusdeletus.security.AuthManager
import com.screenusdeletus.ui.components.PinField
import com.screenusdeletus.ui.components.SectionCard
import com.screenusdeletus.ui.components.StatusRow
import com.screenusdeletus.util.Permissions
import com.screenusdeletus.util.Time
import kotlinx.coroutines.delay

@Composable
fun SecurityScreen() {
    val context = LocalContext.current
    val c = ScreenusDeletusApp.container(context)
    val vm: SecurityViewModel = viewModel { SecurityViewModel(c) }
    val status by vm.status.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(Time.now()) }
    LaunchedEffect(Unit) { while (true) { now = Time.now(); delay(1_000) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Security", "Everything that keeps Screenus Deletus honest.")
        val s = status ?: return@Column

        val (panelBg, panelFg) = when (s.level) {
            ProtectionLevel.NORMAL -> if (s.allProtectionsOn) MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            ProtectionLevel.RESTRICTED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            ProtectionLevel.LOCKDOWN -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = panelBg) {
            Row(Modifier.padding(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    if (s.level == ProtectionLevel.NORMAL && s.allProtectionsOn) Icons.Filled.VerifiedUser else Icons.Filled.GppMaybe,
                    contentDescription = null, tint = panelFg, modifier = Modifier.size(40.dp),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                Column {
                    Text(
                        when {
                            s.level == ProtectionLevel.LOCKDOWN -> "Lockdown"
                            s.level == ProtectionLevel.RESTRICTED -> "Restricted"
                            !s.allProtectionsOn -> "Protection incomplete"
                            else -> "Fully protected"
                        },
                        style = MaterialTheme.typography.headlineSmall, color = panelFg,
                    )
                    Text("Tamper score ${s.tamperScore} in the last 24 h", style = MaterialTheme.typography.bodyMedium, color = panelFg)
                }
            }
        }

        SectionCard("Protection level") {
            Text(
                when (s.level) {
                    ProtectionLevel.NORMAL -> "Normal"
                    ProtectionLevel.RESTRICTED -> "Restricted: every rule is one step stricter"
                    ProtectionLevel.LOCKDOWN -> "Lockdown: every rule is a hard block"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (s.level == ProtectionLevel.NORMAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            s.levelUntil?.let { Text("Until ${Time.formatDateTime(it)}") }
            if (s.level != ProtectionLevel.NORMAL && s.levelUntil == null) Text("Until all protections below are back on.")
            Text("Tamper score (24h): ${s.tamperScore}", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("Protections") {
            StatusRow("Usage access", s.usageAccess) { Permissions.usageAccess(context) }
            StatusRow("Accessibility service", s.accessibilityEnabled) { Permissions.accessibility(context) }
            StatusRow("Device admin", s.deviceAdminActive) { Permissions.deviceAdmin(context) }
            StatusRow("Display over other apps", s.overlayPermission, "Backup blocking") { Permissions.overlay(context) }
            StatusRow("Battery optimisation off", s.ignoringBatteryOptimizations, "Keeps monitoring alive") { Permissions.batteryOptimizations(context) }
        }

        s.integrity?.let { r ->
            SectionCard("Integrity") {
                StatusRow("App signature", r.signatureValid)
                StatusRow("App files unchanged", r.apkIntact)
                StatusRow("No root / hooking", !r.deviceCompromised, (r.rootIndicators + r.hookingIndicators).joinToString("; ").ifEmpty { null })
                StatusRow("USB debugging off", !r.adbEnabled, if (r.adbEnabled) "ADB can grant or revoke permissions" else null)
                if (r.deviceCompromised) Text("On a rooted device every rule is enforced one step stricter.", style = MaterialTheme.typography.bodySmall)
            }
        }

        PauseCard(
            disarmedUntil = s.disarmedUntil,
            requestedAt = s.disarmRequestedAt,
            delayMs = c.tamper.disarmDelayMs,
            now = now,
            guardianName = c.guardian.guardian?.name?.takeIf { c.auth.hasGuardianPin },
            verifyGuardian = { c.auth.verifyGuardianPin(it) },
            onRequest = vm::requestPause,
            onCancel = vm::cancelPause,
            onPauseNow = vm::pauseNow,
        )

        SectionCard("Tamper log") {
            if (events.isEmpty()) Text("Nothing suspicious so far.")
            events.take(30).forEach { e ->
                val label = runCatching { TamperType.valueOf(e.type).label }.getOrDefault(e.type)
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text("${Time.formatDateTime(e.timestamp)}${e.detail?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * The sanctioned way to switch protections off (e.g. to uninstall): request a pause, wait out the
 * delay, then you have 15 minutes in which turning things off isn't counted as tampering.
 */
@Composable
private fun PauseCard(
    disarmedUntil: Long,
    requestedAt: Long,
    delayMs: Long,
    now: Long,
    guardianName: String?,
    verifyGuardian: (String) -> AuthManager.PinResult,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    onPauseNow: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    SectionCard("Pause protection") {
        when {
            disarmedUntil > now -> {
                Text("Paused for ${Time.formatCountdown(disarmedUntil - now)}. You can change permissions or uninstall without it counting as tampering.")
                OutlinedButton(onClick = onCancel) { Text("End pause now") }
            }
            requestedAt > 0 -> {
                Text("Pause starts in ${Time.formatCountdown(requestedAt + delayMs - now)}. Your guardian has been told.")
                OutlinedButton(onClick = onCancel) { Text("Cancel request") }
            }
            else -> {
                Text("Need to turn something off or uninstall? Request a pause. It starts after ${delayMs / 60_000} minutes and lasts 15.")
                Button(onClick = onRequest) { Text("Request pause") }
            }
        }
        if (disarmedUntil <= now && guardianName != null) {
            Text("Or $guardianName can start it immediately:", style = MaterialTheme.typography.bodySmall)
            Row {
                PinField(pin, { pin = it; error = null }, "Guardian PIN", Modifier.weight(1f), isError = error != null)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = {
                when (val r = verifyGuardian(pin)) {
                    AuthManager.PinResult.Ok -> { pin = ""; onPauseNow() }
                    is AuthManager.PinResult.Wrong -> error = "Wrong PIN"
                    is AuthManager.PinResult.LockedOut -> error = "Locked until ${Time.formatClock(r.until)}"
                }
            }) { Text("Guardian: pause now") }
        }
    }
}
