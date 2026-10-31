package com.timeguard.data.repo

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.timeguard.core.RawUsageEvent
import com.timeguard.core.ScreenTimeRule
import com.timeguard.core.UsageAggregator
import com.timeguard.core.UsageSummary
import com.timeguard.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Screen time measurement on top of `UsageStatsManager` events. Everything is computed from the
 * system's own event log, so killing TimeGuard does not create untracked time.
 */
class UsageRepository(
    private val context: Context,
    private val catalog: AppCatalog,
) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    @Volatile
    private var todayCache: Pair<Long, UsageSummary>? = null

    private val ignored: Set<String> by lazy {
        catalog.launcherPackages + setOf(context.packageName, "com.android.systemui")
    }

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Today's usage, cached for a few seconds because enforcement asks for it on every app switch. */
    suspend fun today(maxAgeMs: Long = 5_000): UsageSummary {
        val now = Time.now()
        todayCache?.let { (at, summary) ->
            if (now - at < maxAgeMs && Time.epochDay(at) == Time.epochDay(now)) return summary
        }
        val summary = summary(Time.startOfToday(), now)
        todayCache = now to summary
        return summary
    }

    fun invalidate() {
        todayCache = null
    }

    suspend fun summaryForDay(epochDay: Long): UsageSummary =
        summary(Time.dayStart(epochDay), minOf(Time.dayEnd(epochDay), Time.now()))

    suspend fun summary(start: Long, end: Long): UsageSummary = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) {
            return@withContext UsageSummary(emptyMap(), 0, LongArray(24), emptyMap(), 0)
        }
        // Look back a few hours so sessions that started before `start` are attributed correctly.
        val events = readEvents(start - LOOKBACK_MS, end)
        UsageAggregator.aggregate(
            events = events,
            windowStart = start,
            windowEnd = end,
            hourOf = Time::hourOf,
            hourStart = Time::hourStart,
            ignoredPackages = ignored,
        )
    }

    suspend fun usageForRule(rule: ScreenTimeRule, summary: UsageSummary? = null): Long {
        val s = summary ?: today()
        return s.perPackageMs.entries.sumOf { (pkg, ms) ->
            if (rule.appliesTo(pkg, catalog.categoryOf(pkg))) ms else 0L
        }
    }

    /** Best-effort current foreground app, for when the accessibility service is unavailable. */
    fun currentForegroundApp(): String? {
        if (!hasUsageAccess()) return null
        val now = Time.now()
        val events = usm.queryEvents(now - 60 * 60 * 1000L, now)
        var current: String? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> current = e.packageName
                UsageEvents.Event.ACTIVITY_PAUSED -> if (current == e.packageName) current = null
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> current = null
            }
        }
        return current
    }

    private fun readEvents(start: Long, end: Long): List<RawUsageEvent> {
        val out = ArrayList<RawUsageEvent>(4096)
        val events = usm.queryEvents(start, end) ?: return out
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> RawUsageEvent.Type.FOREGROUND
                UsageEvents.Event.ACTIVITY_PAUSED -> RawUsageEvent.Type.BACKGROUND
                UsageEvents.Event.SCREEN_INTERACTIVE -> RawUsageEvent.Type.SCREEN_ON
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> RawUsageEvent.Type.SCREEN_OFF
                UsageEvents.Event.DEVICE_SHUTDOWN -> RawUsageEvent.Type.DEVICE_SHUTDOWN
                else -> null
            } ?: continue
            out += RawUsageEvent(e.timeStamp, e.packageName ?: "", type)
        }
        return out
    }

    companion object {
        private const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}
