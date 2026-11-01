package com.screenusdeletus.ui.screens

import com.screenusdeletus.ui.components.ValueSlider
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.AppCategory
import com.screenusdeletus.core.EscalationLevel
import com.screenusdeletus.core.ScreenTimeRule
import com.screenusdeletus.core.TimeRange
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.data.repo.InstalledApp
import com.screenusdeletus.data.repo.RulesRepository
import com.screenusdeletus.data.repo.SaveResult
import com.screenusdeletus.security.AuthManager
import com.screenusdeletus.ui.components.PinField
import com.screenusdeletus.ui.components.SectionCard
import com.screenusdeletus.util.Time
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(id: String, onDone: () -> Unit) {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    val scope = rememberCoroutineScope()
    val isNew = id == "new"
    val ruleId = remember { if (isNew) RulesRepository.newId() else id }

    var loaded by remember { mutableStateOf(isNew) }
    var createdAt by remember { mutableStateOf(Time.now()) }
    var name by remember { mutableStateOf("") }
    var packages by remember { mutableStateOf(setOf<String>()) }
    var categories by remember { mutableStateOf(setOf<AppCategory>()) }
    var hasLimit by remember { mutableStateOf(true) }
    var limitMinutes by remember { mutableFloatStateOf(30f) }
    var ranges by remember { mutableStateOf(listOf<TimeRange>()) }
    var level by remember { mutableStateOf(EscalationLevel.MEDIUM) }
    var enabled by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var addingRange by remember { mutableStateOf(false) }
    var scheduled by remember { mutableStateOf<Pair<Long, ScreenTimeRule?>?>(null) } // appliesAt, rule (null = delete)

    LaunchedEffect(id) {
        if (!isNew) {
            c.rules.observe().first().firstOrNull { it.rule.id == id }?.let { s ->
                // Edit the pending version if there is one, so the user sees what they last asked for.
                val r = s.pending ?: s.rule
                createdAt = r.createdAt; name = r.name; packages = r.targetPackages; categories = r.targetCategories
                hasLimit = r.dailyLimitMs >= 0; limitMinutes = if (r.dailyLimitMs >= 0) r.dailyLimitMs / 60_000f else 30f
                ranges = r.blockedHours; level = r.escalationLevel; enabled = r.enabled
            }
            loaded = true
        }
    }
    val apps by produceState(emptyList<InstalledApp>()) { value = c.catalog.launchableApps() }

    fun build() = ScreenTimeRule(
        id = ruleId,
        name = name.ifBlank { "Rule" },
        targetPackages = packages,
        targetCategories = categories,
        dailyLimitMs = if (hasLimit) (limitMinutes.toLong() * 60_000) else ScreenTimeRule.NO_LIMIT,
        blockedHours = ranges,
        escalationLevel = level,
        enabled = enabled,
        createdAt = createdAt,
    )

    if (!loaded) return

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (isNew) "New rule" else "Edit rule", style = MaterialTheme.typography.headlineMedium)
        if (!isNew) {
            Text(
                "Tightening applies now. Loosening (higher limit, softer level, fewer apps or hours, disabling) waits " +
                    "${c.rules.weakeningDelayMs / 3_600_000}h — or your guardian can approve it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)

        SectionCard("Enforcement") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EscalationLevel.entries.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = level == l,
                        onClick = { level = l },
                        shape = SegmentedButtonDefaults.itemShape(i, EscalationLevel.entries.size),
                    ) { Text(l.label()) }
                }
            }
            Text(
                when (level) {
                    EscalationLevel.SOFT -> "Notifications only. Going over still breaks your streak."
                    EscalationLevel.MEDIUM -> "A reflection screen with a forced wait that grows each time."
                    EscalationLevel.HARD -> "The app won't open. Only an emergency unlock gets you in."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rule enabled", Modifier.weight(1f))
                Switch(enabled, { enabled = it })
            }
        }

        SectionCard("Daily limit") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (hasLimit) "Limit daily use" else "No daily limit (focus hours only)", Modifier.weight(1f))
                Switch(hasLimit, { hasLimit = it })
            }
            if (hasLimit) ValueSlider("Allowed per day", if (limitMinutes == 0f) "Blocked" else formatDuration(limitMinutes.toLong() * 60_000), limitMinutes, { limitMinutes = it }, 0f..240f, step = 5f)
        }

        SectionCard("Focus hours (blocked)", action = { TextButton(onClick = { addingRange = true }) { Text("Add") } }) {
            if (ranges.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall)
            ranges.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.format() + if (r.days.isEmpty()) " · every day" else " · " + r.days.sorted().joinToString { DAY_NAMES[it - 1] }, Modifier.weight(1f))
                    IconButton(onClick = { ranges = ranges.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Filled.Close, "Remove") }
                }
            }
        }

        SectionCard("Categories") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppCategory.entries.forEach { cat ->
                    FilterChip(cat in categories, { categories = if (cat in categories) categories - cat else categories + cat }, { Text(cat.label) })
                }
            }
        }

        SectionCard("Apps (${packages.size} selected)") {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search apps") }, singleLine = true)
            val filtered = apps.filter { search.isBlank() || it.label.contains(search, true) || it.packageName.contains(search, true) }
                .sortedByDescending { it.packageName in packages }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            packages = if (app.packageName in packages) packages - app.packageName else packages + app.packageName
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(app.packageName in packages, null)
                        Column(Modifier.weight(1f)) {
                            Text(app.label)
                            Text(app.category.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = packages.isNotEmpty() || categories.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val rule = build()
                        when (val r = c.rules.save(rule)) {
                            SaveResult.Applied -> onDone()
                            is SaveResult.Scheduled -> scheduled = r.appliesAt to rule
                        }
                    }
                },
            ) { Text("Save") }
            if (!isNew) {
                OutlinedButton(onClick = {
                    scope.launch {
                        when (val r = c.rules.delete(ruleId)) {
                            SaveResult.Applied -> onDone()
                            is SaveResult.Scheduled -> scheduled = r.appliesAt to null
                        }
                    }
                }) { Text("Delete") }
            }
            TextButton(onClick = onDone) { Text("Cancel") }
        }
    }

    if (addingRange) TimeRangeDialog(onDismiss = { addingRange = false }) { ranges = ranges + it; addingRange = false }

    scheduled?.let { (at, rule) ->
        val g = c.guardian.guardian
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { scheduled = null; onDone() },
            title = { Text("Change scheduled") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This loosens your rules, so it applies at ${Time.formatDateTime(at)}. Until then the current rule stays in force.")
                    if (g != null && g.canModifyRules && c.auth.hasGuardianPin) {
                        Text("${g.name} can approve it now:")
                        PinField(pin, { pin = it; error = null }, "Guardian PIN", isError = error != null)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                if (g != null && g.canModifyRules && c.auth.hasGuardianPin) {
                    TextButton(onClick = {
                        when (val res = c.auth.verifyGuardianPin(pin)) {
                            AuthManager.PinResult.Ok -> scope.launch {
                                if (rule != null) c.rules.save(rule, bypassCoolingOff = true) else c.rules.delete(ruleId, bypassCoolingOff = true)
                                scheduled = null
                                onDone()
                            }
                            is AuthManager.PinResult.Wrong -> error = "Wrong PIN"
                            is AuthManager.PinResult.LockedOut -> error = "Locked until ${Time.formatClock(res.until)}"
                        }
                    }) { Text("Approve now") }
                }
            },
            dismissButton = { TextButton(onClick = { scheduled = null; onDone() }) { Text("OK, wait") } },
        )
    }
}

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeRangeDialog(onDismiss: () -> Unit, onAdd: (TimeRange) -> Unit) {
    var start by remember { mutableStateOf("22:00") }
    var end by remember { mutableStateOf("07:00") }
    var days by remember { mutableStateOf(setOf<Int>()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked hours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, Modifier.weight(1f), label = { Text("From") }, singleLine = true)
                    OutlinedTextField(end, { end = it }, Modifier.weight(1f), label = { Text("To") }, singleLine = true)
                }
                Text("Days (none = every day)", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_NAMES.forEachIndexed { i, d ->
                        FilterChip((i + 1) in days, { days = if ((i + 1) in days) days - (i + 1) else days + (i + 1) }, { Text(d) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { TimeRange.parse("$start-$end", days) }
                    .onSuccess(onAdd)
                    .onFailure { error = "Use HH:mm, e.g. 09:00" }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
