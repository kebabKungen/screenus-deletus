package com.screenusdeletus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.ui.components.EmergencyUnlockFlow
import com.screenusdeletus.util.Time

@Composable
fun UnlockScreen(onDone: () -> Unit) {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    var until by remember { mutableLongStateOf(c.unlocks.unlockedUntil) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (until > Time.now()) {
            Text("Unlocked until ${Time.formatClock(until)}", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = { c.unlocks.endUnlockNow(); until = 0 }) { Text("End unlock now") }
        } else {
            EmergencyUnlockFlow { onDone() }
        }
    }
}
