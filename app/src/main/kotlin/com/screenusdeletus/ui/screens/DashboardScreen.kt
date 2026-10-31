package com.screenusdeletus.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.ProtectionLevel
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.data.repo.AppCatalog
import com.screenusdeletus.ui.components.Badge
import com.screenusdeletus.ui.components.Eyebrow
import com.screenusdeletus.ui.components.HeroPanel
import com.screenusdeletus.ui.components.LogoMark
import com.screenusdeletus.ui.components.MeterBar
import com.screenusdeletus.ui.components.SectionCard
import com.screenusdeletus.ui.components.StatTile
import com.screenusdeletus.ui.components.UsageRing
import com.screenusdeletus.ui.theme.Brand
import com.screenusdeletus.util.Time

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    val vm: StatsViewModel = viewModel { StatsViewModel(c) }
    val data by vm.data.collectAsStateWithLifecycle()
    val protection by vm.protection.collectAsStateWithLifecycle()
    val extra = Brand.extra
    val motivation = remember { c.profile.profile.motivation }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(greeting(), style = MaterialTheme.typography.titleLarge)
                Text("Screenus Deletus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val d = data
        HeroPanel {
            Eyebrow("Today", color = extra.onHero.copy(alpha = 0.7f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsageRing(
                    usedMs = d?.todayUsageMs ?: 0L,
                    goalMs = d?.goalMs ?: c.profile.dailyGoalMs,
                    size = 132.dp,
                    track = extra.onHero.copy(alpha = 0.16f),
                    progress = extra.onHero,
                    over = MaterialTheme.colorScheme.tertiaryContainer,
                    text = extra.onHero,
                    subText = extra.onHero.copy(alpha = 0.7f),
                    strokeWidth = 12.dp,
                )
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (d == null) {
                        CircularProgressIndicator(color = extra.onHero, modifier = Modifier.size(28.dp))
                    } else {
                        val left = d.goalMs - d.todayUsageMs
                        Text(
                            formatDuration(kotlin.math.abs(left)),
                            style = MaterialTheme.typography.displaySmall,
                            color = if (left >= 0) extra.onHero else MaterialTheme.colorScheme.tertiaryContainer,
                        )
                        Text(
                            if (left >= 0) "left of your daily goal" else "over your daily goal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = extra.onHero.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val p = protection
                val (label, bg) = when {
                    p == null -> "Checking…" to extra.onHero.copy(alpha = 0.16f)
                    p.level == ProtectionLevel.LOCKDOWN -> "Lockdown" to MaterialTheme.colorScheme.error
                    p.level == ProtectionLevel.RESTRICTED || !p.allProtectionsOn -> "Restricted" to MaterialTheme.colorScheme.tertiary
                    else -> "Protected" to extra.onHero.copy(alpha = 0.16f)
                }
                Badge(label, bg, extra.onHero)
                if (d != null) Badge(
                    if (d.streak.currentStreak == 0) "Streak starts today" else "${d.streak.currentStreak}-day streak",
                    extra.onHero.copy(alpha = 0.16f), extra.onHero,
                )
                if (vm.unlockedUntil > Time.now()) Badge("Unlocked to ${Time.formatClock(vm.unlockedUntil)}", MaterialTheme.colorScheme.tertiary, extra.onHero)
            }
        }

        if (motivation.isNotBlank()) {
            Text(
                "“$motivation”",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        protection?.let { p ->
            if (!p.allProtectionsOn || p.level != ProtectionLevel.NORMAL) {
                AlertBanner(
                    icon = Icons.Filled.WarningAmber,
                    title = when (p.level) {
                        ProtectionLevel.LOCKDOWN -> "Lockdown mode"
                        ProtectionLevel.RESTRICTED -> "Restricted mode"
                        ProtectionLevel.NORMAL -> "Protection incomplete"
                    },
                    text = if (!p.allProtectionsOn) "A required permission is off, so every rule is enforced one step stricter."
                    else "Tampering was detected" + (p.levelUntil?.let { " — strict until ${Time.formatClock(it)}" } ?: "") + ".",
                    action = "Review",
                    onAction = { onNavigate("security") },
                )
            }
        }

        if (d == null) return@Column

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Filled.PhoneAndroid, formatDuration(d.screenOnMs), "Screen on", Modifier.weight(1f))
            StatTile(Icons.Filled.TouchApp, d.pickups.toString(), "Pickups", Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
            StatTile(Icons.Filled.ShowChart, formatDuration(d.weeklyAverageMs), "7-day avg", Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Filled.LocalFireDepartment, "${d.streak.currentStreak} days", "Best ${d.streak.longestStreak}", Modifier.weight(1f), extra.warning)
            StatTile(Icons.Filled.Verified, "${d.complianceScore.toInt()}%", "Compliance", Modifier.weight(1f), extra.success)
            StatTile(Icons.Filled.LockOpen, "${d.unlocksThisWeek}/${d.maxUnlocksPerWeek}", "Unlocks", Modifier.weight(1f), MaterialTheme.colorScheme.error)
        }

        SectionCard("Top apps today") {
            if (d.appBreakdown.isEmpty()) {
                Text("Nothing yet today. Nice.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val max = d.appBreakdown.first().second.coerceAtLeast(1)
                d.appBreakdown.take(5).forEach { (pkg, ms) -> AppUsageRow(c.catalog, pkg, ms, ms.toFloat() / max) }
            }
        }

        if (d.insights.isNotEmpty()) {
            SectionCard("Insights", action = { TextButton(onClick = { onNavigate("insights") }) { Text("All") } }) {
                d.insights.take(3).forEach { InsightRow(it) }
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
fun AppUsageRow(catalog: AppCatalog, pkg: String, ms: Long, fraction: Float) {
    val icon = remember(pkg) { runCatching { catalog.icon(pkg)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            Box(Modifier.size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(catalog.label(pkg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                Text(formatDuration(ms), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MeterBar(fraction, height = 6.dp, over = false)
        }
    }
}

@Composable
fun InsightRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AlertBanner(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}, container: Color = MaterialTheme.colorScheme.errorContainer, content: Color = MaterialTheme.colorScheme.onErrorContainer) {
    Surface(shape = RoundedCornerShape(20.dp), color = container) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = content)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = content)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
                if (action != null) TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(action, color = content) }
            }
        }
    }
}

private fun greeting(): String = when (Time.minuteOfDay() / 60) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Up late"
}
