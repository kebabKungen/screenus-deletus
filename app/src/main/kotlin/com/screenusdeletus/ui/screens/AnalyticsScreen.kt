package com.screenusdeletus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.ui.components.BarChart
import com.screenusdeletus.ui.components.DonutChart
import com.screenusdeletus.ui.components.HeatMap
import com.screenusdeletus.ui.components.LabeledBar
import com.screenusdeletus.ui.components.SectionCard
import com.screenusdeletus.util.Time

@Composable
fun AnalyticsScreen() {
    val c = ScreenusDeletusApp.container(LocalContext.current)
    val vm: StatsViewModel = viewModel { StatsViewModel(c) }
    val data by vm.data.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Insights", style = MaterialTheme.typography.headlineMedium)
        val d = data
        if (d == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }

        SectionCard("What stands out") {
            if (d.insights.isEmpty()) Text("Not enough data yet — check back after a few days.")
            d.insights.forEach { Text("• $it") }
        }

        SectionCard("Last 7 days") {
            val today = Time.epochDay()
            val byDay = d.last30Days.associateBy { it.epochDay }
            val days = (today - 6..today).toList()
            BarChart(
                values = days.map { if (it == today) d.todayUsageMs else byDay[it]?.usageMs ?: 0L },
                labels = days.map { Time.shortDay(it) },
                goal = d.goalMs,
            )
            Text("Dashed line: daily goal (${formatDuration(d.goalMs)}). Red bars went over.", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("Last 30 days") {
            val today = Time.epochDay()
            val byDay = d.last30Days.associateBy { it.epochDay }
            val days = (today - 29..today).toList()
            BarChart(
                values = days.map { if (it == today) d.todayUsageMs else byDay[it]?.usageMs ?: 0L },
                labels = days.mapIndexed { i, day -> if (i % 7 == 0) Time.shortDay(day) else "" },
                goal = d.goalMs,
                height = 120.dp,
            )
        }

        SectionCard("When you use your phone") {
            Text("Average by day and hour, last 4 weeks", style = MaterialTheme.typography.bodySmall)
            HeatMap(d.heatMap, Modifier.fillMaxWidth())
        }

        if (d.categoryBreakdown.isNotEmpty()) {
            SectionCard("Categories today") {
                DonutChart(d.categoryBreakdown.entries.take(7).map { it.key to it.value })
            }
        }

        SectionCard("Triggers this week") {
            if (d.triggerAnalysis.isEmpty()) {
                Text("When a block screen asks why you reached for your phone, your answers show up here.")
            } else {
                val max = d.triggerAnalysis.values.max().toFloat()
                d.triggerAnalysis.forEach { (t, n) -> LabeledBar(t, "$n×", n / max, MaterialTheme.colorScheme.tertiary) }
            }
        }

        SectionCard("Accountability") {
            Text("Emergency unlocks this week: ${d.unlocksThisWeek}/${d.maxUnlocksPerWeek}")
            Text("30-day compliance: ${d.complianceScore.toInt()}%")
            Text("Streak: ${d.streak.currentStreak} days · best ${d.streak.longestStreak}")
            if (d.streak.streakBrokenDays.isNotEmpty()) {
                Text("Streak broken on: " + d.streak.streakBrokenDays.takeLast(5).joinToString { Time.shortDay(it) }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
