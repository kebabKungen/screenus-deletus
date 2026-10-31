package com.screenusdeletus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.ProtectionLevel
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.ui.components.LabeledBar
import com.screenusdeletus.ui.components.SectionCard
import com.screenusdeletus.ui.components.UsageRing
import com.screenusdeletus.util.Time

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    val vm: StatsViewModel = viewModel { StatsViewModel(c) }
    val data by vm.data.collectAsStateWithLifecycle()
    val protection by vm.protection.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineMedium)

        protection?.let { p ->
            if (!p.allProtectionsOn || p.level != ProtectionLevel.NORMAL) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            when (p.level) {
                                ProtectionLevel.LOCKDOWN -> "Lockdown mode"
                                ProtectionLevel.RESTRICTED -> "Restricted mode"
                                ProtectionLevel.NORMAL -> "Protection incomplete"
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            if (!p.allProtectionsOn) "A required permission is off, so rules are enforced more strictly." else
                                "Tampering was detected" + (p.levelUntil?.let { " — until ${Time.formatClock(it)}" } ?: "") + ".",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { onNavigate("security") }) { Text("Review") }
                    }
                }
            }
        }

        if (vm.unlockedUntil > Time.now()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text("Emergency unlock active until ${Time.formatClock(vm.unlockedUntil)}", Modifier.padding(16.dp))
            }
        }

        val d = data
        if (d == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }

        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { UsageRing(d.todayUsageMs, d.goalMs) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Screen on", formatDuration(d.screenOnMs))
                Stat("Pickups", d.pickups.toString())
                Stat("7-day avg", formatDuration(d.weeklyAverageMs))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(modifier = Modifier.weight(1f)) {
                Text("Streak", style = MaterialTheme.typography.labelLarge)
                Text("${d.streak.currentStreak} days", style = MaterialTheme.typography.headlineSmall)
                Text("Best ${d.streak.longestStreak}", style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(modifier = Modifier.weight(1f)) {
                Text("Compliance", style = MaterialTheme.typography.labelLarge)
                Text("${d.complianceScore.toInt()}%", style = MaterialTheme.typography.headlineSmall)
                Text("Unlocks ${d.unlocksThisWeek}/${d.maxUnlocksPerWeek} this week", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (d.appBreakdown.isNotEmpty()) {
            SectionCard("Top apps today") {
                val max = d.appBreakdown.first().second.coerceAtLeast(1)
                d.appBreakdown.take(6).forEach { (pkg, ms) ->
                    LabeledBar(c.catalog.label(pkg), formatDuration(ms), ms.toFloat() / max)
                }
            }
        }

        if (d.insights.isNotEmpty()) {
            SectionCard("Insights") {
                d.insights.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = { onNavigate("insights") }) { Text("See all") }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
