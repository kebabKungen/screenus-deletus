package com.screenusdeletus.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.data.repo.TrustedGuardian
import com.screenusdeletus.security.AuthManager
import com.screenusdeletus.ui.components.PinField
import com.screenusdeletus.ui.components.SectionCard
import com.screenusdeletus.util.Permissions
import com.screenusdeletus.util.Time
import kotlinx.coroutines.launch

@Composable
fun GuardianScreen() {
    val context = LocalContext.current
    val c = ScreenusDeletusApp.container(context)
    val scope = rememberCoroutineScope()
    val existing = remember { c.guardian.guardian }
    val outbox by c.guardian.observeOutbox().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var relationship by remember { mutableStateOf(existing?.relationshipType ?: "Friend") }
    var approve by remember { mutableStateOf(existing?.canApproveUnlocks ?: true) }
    var modify by remember { mutableStateOf(existing?.canModifyRules ?: true) }
    var sms by remember { mutableStateOf(existing?.smsAlerts ?: false) }
    var weekly by remember { mutableStateOf(existing?.weeklyReportDelivery ?: true) }
    var newPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var configured by remember { mutableStateOf(c.guardian.isConfigured) }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) sms = false
    }

    fun guardianOk(): Boolean {
        if (!c.auth.hasGuardianPin) return true
        return when (val r = c.auth.verifyGuardianPin(currentPin)) {
            AuthManager.PinResult.Ok -> true
            is AuthManager.PinResult.Wrong -> { message = "Wrong guardian PIN"; false }
            is AuthManager.PinResult.LockedOut -> { message = "Locked until ${Time.formatClock(r.until)}"; false }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Guardian", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A friend, family member, partner or therapist who approves emergency unlocks and early rule changes with " +
                "their own PIN, and hears about tamper attempts. Hand them the phone to set their PIN.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard("Who") {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(relationship, { relationship = it }, Modifier.fillMaxWidth(), label = { Text("Relationship") }, singleLine = true)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone (for SMS alerts)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        }

        SectionCard("Powers") {
            Toggle("Must approve every emergency unlock", approve) { approve = it }
            Toggle("Can approve rule loosening immediately", modify) { modify = it }
            Toggle("Text alerts automatically (SMS)", sms) {
                sms = it
                if (it && !c.guardian.canSendSms()) smsLauncher.launch(Manifest.permission.SEND_SMS)
            }
            Toggle("Weekly report", weekly) { weekly = it }
        }

        SectionCard("Guardian PIN") {
            if (c.auth.hasGuardianPin) {
                Text("Changing or removing the guardian needs the current guardian PIN.", style = MaterialTheme.typography.bodySmall)
                PinField(currentPin, { currentPin = it }, "Current guardian PIN")
            }
            PinField(newPin, { newPin = it }, if (c.auth.hasGuardianPin) "New guardian PIN (optional)" else "Guardian sets a PIN")
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = name.isNotBlank() && (c.auth.hasGuardianPin || newPin.length >= AuthManager.MIN_PIN_LENGTH),
                onClick = {
                    if (!guardianOk()) return@Button
                    if (newPin.isNotEmpty()) {
                        if (newPin.length < AuthManager.MIN_PIN_LENGTH) { message = "PIN too short"; return@Button }
                        c.auth.setGuardianPin(newPin)
                    }
                    c.guardian.guardian = TrustedGuardian(name.trim(), email.trim(), phone.trim(), relationship.trim(), approve, modify, sms, weekly)
                    // Guardian-approved unlocks become mandatory when the toggle is on.
                    c.unlocks.updateSettings(c.unlocks.settings.copy(requireGuardianApproval = approve), bypass = true)
                    newPin = ""; currentPin = ""
                    configured = true
                    message = "Saved"
                },
            ) { Text("Save") }
            if (configured) {
                OutlinedButton(onClick = {
                    if (!guardianOk()) return@OutlinedButton
                    scope.launch { c.guardian.notify("Guardian removed", "You were removed as guardian.") }
                    c.guardian.guardian = null
                    c.auth.clearGuardianPin()
                    c.unlocks.updateSettings(c.unlocks.settings.copy(requireGuardianApproval = false), bypass = true)
                    configured = false
                    message = "Guardian removed"
                }) { Text("Remove") }
            }
        }

        if (configured) {
            SectionCard("Messages waiting to be sent (${outbox.size})") {
                if (outbox.isEmpty()) Text("Nothing pending.")
                outbox.takeLast(10).forEach { m ->
                    Text("${Time.formatDateTime(m.createdAt)} · ${m.subject}", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (outbox.isNotEmpty()) {
                        Button(onClick = {
                            val body = outbox.joinToString("\n\n") { "[${Time.formatDateTime(it.createdAt)}] ${it.subject}\n${it.body}" }
                            if (Permissions.email(context, email, "Screenus Deletus updates", body)) {
                                scope.launch { c.guardian.markSent(outbox.map { it.id }) }
                            }
                        }) { Text("Send by email") }
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val report = c.stats.weeklyReport()
                            Permissions.email(context, email, "Screenus Deletus weekly report", report)
                        }
                    }) { Text("Email report now") }
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}
