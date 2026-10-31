package com.screenusdeletus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.EscalationLevel
import com.screenusdeletus.core.ScreenTimeRule
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.ui.components.SectionCard
import com.screenusdeletus.util.Time

fun EscalationLevel.label(): String = when (this) {
    EscalationLevel.SOFT -> "Warn"
    EscalationLevel.MEDIUM -> "Pause"
    EscalationLevel.HARD -> "Block"
}

@Composable
fun RulesScreen(onEdit: (String) -> Unit) {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    val vm: RulesViewModel = viewModel { RulesViewModel(c) }
    val rules by vm.rules.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Rules", style = MaterialTheme.typography.headlineMedium) }
            if (rules.isEmpty()) item { Text("No rules yet. Add one to start.") }
            items(rules, key = { it.rule.id }) { stored ->
                val r = stored.rule
                SectionCard(r.name, modifier = Modifier.clickable { onEdit(r.id) }, action = {
                    AssistChip(onClick = { onEdit(r.id) }, label = { Text(if (r.enabled) r.escalationLevel.label() else "Off") })
                }) {
                    Text(targetsSummary(r, c.catalog::label), style = MaterialTheme.typography.bodySmall)
                    val used = usage[r.id] ?: 0L
                    if (r.dailyLimitMs >= 0) {
                        Text("${formatDuration(used)} of ${formatDuration(r.dailyLimitMs)} today")
                        LinearProgressIndicator(
                            progress = { if (r.dailyLimitMs == 0L) 1f else (used.toFloat() / r.dailyLimitMs).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (r.blockedHours.isNotEmpty()) Text("Blocked " + r.blockedHours.joinToString { it.format() })
                    if (!stored.integrityOk) Text("Integrity check failed — enforced as a hard block", color = MaterialTheme.colorScheme.error)
                    if (stored.pendingAppliesAt != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (stored.pendingDelete) "Deletion" else "Loosened version") +
                                    " applies ${Time.formatDateTime(stored.pendingAppliesAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.cancelPending(r.id) }) { Text("Cancel change") }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onEdit("new") },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add rule") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

fun targetsSummary(r: ScreenTimeRule, label: (String) -> String): String {
    val parts = r.targetCategories.map { it.label } + r.targetPackages.map(label)
    return when {
        parts.isEmpty() -> "No apps selected"
        parts.size <= 4 -> parts.joinToString()
        else -> parts.take(4).joinToString() + " +${parts.size - 4} more"
    }
}
