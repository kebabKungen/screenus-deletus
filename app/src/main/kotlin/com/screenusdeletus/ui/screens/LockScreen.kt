package com.screenusdeletus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.security.AuthManager
import com.screenusdeletus.ui.components.PinField
import com.screenusdeletus.util.Time

@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val biometric = c.auth.biometricEnabled && c.auth.canUseBiometrics(activity)

    fun promptBio() = c.auth.promptBiometric(activity, "Screenus Deletus", onSuccess = onUnlocked, onFallback = {})
    LaunchedEffect(Unit) { if (biometric) promptBio() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Screenus Deletus", style = MaterialTheme.typography.headlineMedium)
            PinField(pin, { pin = it; error = null }, "PIN", isError = error != null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    when (val r = c.auth.verifyPin(pin)) {
                        AuthManager.PinResult.Ok -> onUnlocked()
                        is AuthManager.PinResult.Wrong -> {
                            error = if (r.attemptsBeforeLockout > 0) "Wrong PIN · ${r.attemptsBeforeLockout} tries left" else "Wrong PIN"
                            pin = ""
                        }
                        is AuthManager.PinResult.LockedOut -> error = "Locked until ${Time.formatClock(r.until)}"
                    }
                },
                enabled = pin.length >= AuthManager.MIN_PIN_LENGTH,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Unlock") }
            if (biometric) TextButton(onClick = { promptBio() }) { Text("Use fingerprint or face") }
        }
    }
}
