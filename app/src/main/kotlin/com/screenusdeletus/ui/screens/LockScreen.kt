package com.screenusdeletus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
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
import com.screenusdeletus.ui.components.LogoMark
import com.screenusdeletus.ui.components.PinPad
import com.screenusdeletus.util.Time

@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val biometric = c.auth.biometricEnabled && c.auth.canUseBiometrics(activity)

    fun promptBio() = c.auth.promptBiometric(activity, "Screenus Deletus", onSuccess = onUnlocked, onFallback = {})
    LaunchedEffect(Unit) { if (biometric) promptBio() }

    fun submit() {
        when (val r = c.auth.verifyPin(pin)) {
            AuthManager.PinResult.Ok -> onUnlocked()
            is AuthManager.PinResult.Wrong -> {
                error = if (r.attemptsBeforeLockout > 0) "Wrong PIN · ${r.attemptsBeforeLockout} tries left" else "Wrong PIN"
                pin = ""
            }
            is AuthManager.PinResult.LockedOut -> {
                error = "Too many attempts. Try again at ${Time.formatClock(r.until)}"
                pin = ""
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            LogoMark(size = 64.dp)
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
            Text(
                error ?: "Enter your PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            PinPad(pin, { pin = it; error = null }, error = error != null)
            Button(
                onClick = { submit() },
                enabled = pin.length >= AuthManager.MIN_PIN_LENGTH,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.width(256.dp).height(52.dp),
            ) { Text("Unlock", style = MaterialTheme.typography.titleMedium) }
            if (biometric) {
                TextButton(onClick = { promptBio() }) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use biometrics")
                }
            }
        }
    }
}
