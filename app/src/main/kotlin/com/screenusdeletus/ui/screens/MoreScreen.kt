package com.screenusdeletus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.screenusdeletus.ui.components.ScreenHeader

private data class Entry(val route: String, val title: String, val subtitle: String, val icon: ImageVector)

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val entries = listOf(
        Entry("guardian", "Guardian", "Someone who approves unlocks and gets alerts", Icons.Filled.People),
        Entry("commitment", "Commitment pledge", "Owe a donation for every slip", Icons.Filled.Savings),
        Entry("unlock", "Emergency unlock", "When you genuinely need an app", Icons.Filled.LockOpen),
        Entry("settings", "Settings", "Goal, unlock rules, delays, PIN", Icons.Filled.Settings),
    )
    val tints = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.secondary,
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("More", "Accountability, unlocks and settings.")
        entries.forEachIndexed { i, e -> EntryCard(e, tints[i]) { onNavigate(e.route) } }
    }
}

@Composable
private fun EntryCard(e: Entry, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(e.icon, contentDescription = null, tint = tint)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                Text(e.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
