package com.screenusdeletus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val items = listOf(
        Triple("guardian", "Guardian", Icons.Filled.People) to "Someone who approves unlocks and gets alerts",
        Triple("commitment", "Commitment pledge", Icons.Filled.Savings) to "Owe a donation for every slip",
        Triple("unlock", "Emergency unlock", Icons.Filled.LockOpen) to "When you genuinely need an app",
        Triple("settings", "Settings", Icons.Filled.Settings) to "Goal, unlock rules, delays, PIN",
    )
    Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        Text("More", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp))
        items.forEach { (t, sub) ->
            val (route, title, icon: ImageVector) = t
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(sub) },
                leadingContent = { Icon(icon, contentDescription = null) },
                modifier = Modifier.clickable { onNavigate(route) },
            )
            HorizontalDivider()
        }
    }
}
