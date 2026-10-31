package com.screenusdeletus.core

/** Minimal, platform-independent view of an Android `UsageEvents.Event`. */
data class RawUsageEvent(
    val timestamp: Long,
    val packageName: String,
    val type: Type,
) {
    enum class Type { FOREGROUND, BACKGROUND, SCREEN_ON, SCREEN_OFF, DEVICE_SHUTDOWN }
}

data class UsageSummary(
    val perPackageMs: Map<String, Long>,
    val screenOnMs: Long,
    /** 24 buckets of total foreground app time, by local hour of day. */
    val hourlyMs: LongArray,
    /** Number of times each package was brought to the foreground. */
    val launches: Map<String, Int>,
    /** Number of times the screen was turned on (a proxy for "pickups"). */
    val pickups: Int,
) {
    val totalAppMs: Long get() = perPackageMs.values.sum()

    override fun equals(other: Any?): Boolean =
        other is UsageSummary && perPackageMs == other.perPackageMs && screenOnMs == other.screenOnMs &&
            hourlyMs.contentEquals(other.hourlyMs) && launches == other.launches && pickups == other.pickups

    override fun hashCode(): Int =
        31 * (31 * perPackageMs.hashCode() + screenOnMs.hashCode()) + hourlyMs.contentHashCode()
}

/**
 * Turns a stream of foreground/background events into durations. Handles the usual quirks of
 * `UsageStatsManager` data: sessions that began before the window, sessions still open at the
 * end of the window, missing BACKGROUND events (closed by the next FOREGROUND or SCREEN_OFF),
 * and duplicate FOREGROUND events.
 */
object UsageAggregator {

    /**
     * @param hourOf maps a timestamp to its local hour (0..23); supplied by the caller so this
     *   stays time-zone agnostic.
     * @param hourStart maps a timestamp to the start of its local hour.
     * @param ignoredPackages packages whose time is not counted (launcher, Screenus Deletus itself…).
     */
    fun aggregate(
        events: List<RawUsageEvent>,
        windowStart: Long,
        windowEnd: Long,
        hourOf: (Long) -> Int,
        hourStart: (Long) -> Long,
        ignoredPackages: Set<String> = emptySet(),
        /** Package that was already in the foreground when the window opened, if known. */
        initialForeground: String? = null,
        initialScreenOn: Boolean = initialForeground != null,
    ): UsageSummary {
        val perPackage = HashMap<String, Long>()
        val launches = HashMap<String, Int>()
        val hourly = LongArray(24)
        var screenOnMs = 0L
        var pickups = 0

        var current: String? = initialForeground
        var currentSince = windowStart
        var screenOnSince: Long? = if (initialScreenOn) windowStart else null

        fun closeCurrent(at: Long) {
            val pkg = current ?: return
            val start = maxOf(currentSince, windowStart)
            val end = minOf(at, windowEnd)
            if (end > start && pkg !in ignoredPackages) {
                perPackage[pkg] = (perPackage[pkg] ?: 0L) + (end - start)
                spreadOverHours(start, end, hourly, hourOf, hourStart)
            }
            current = null
        }

        fun closeScreen(at: Long) {
            val since = screenOnSince ?: return
            val start = maxOf(since, windowStart)
            val end = minOf(at, windowEnd)
            if (end > start) screenOnMs += end - start
            screenOnSince = null
        }

        for (e in events.sortedBy { it.timestamp }) {
            if (e.timestamp > windowEnd) break
            when (e.type) {
                RawUsageEvent.Type.FOREGROUND -> {
                    if (current == e.packageName) continue // duplicate resume (multiple activities)
                    closeCurrent(e.timestamp)
                    current = e.packageName
                    currentSince = e.timestamp
                    if (e.timestamp >= windowStart && e.packageName !in ignoredPackages) {
                        launches[e.packageName] = (launches[e.packageName] ?: 0) + 1
                    }
                    if (screenOnSince == null) screenOnSince = e.timestamp
                }
                RawUsageEvent.Type.BACKGROUND -> if (current == e.packageName) closeCurrent(e.timestamp)
                RawUsageEvent.Type.SCREEN_ON -> {
                    if (screenOnSince == null) {
                        screenOnSince = e.timestamp
                        if (e.timestamp >= windowStart) pickups++
                    }
                }
                RawUsageEvent.Type.SCREEN_OFF, RawUsageEvent.Type.DEVICE_SHUTDOWN -> {
                    closeCurrent(e.timestamp)
                    closeScreen(e.timestamp)
                }
            }
        }
        closeCurrent(windowEnd)
        closeScreen(windowEnd)

        return UsageSummary(perPackage, screenOnMs, hourly, launches, pickups)
    }

    private fun spreadOverHours(
        start: Long,
        end: Long,
        hourly: LongArray,
        hourOf: (Long) -> Int,
        hourStart: (Long) -> Long,
    ) {
        var t = start
        while (t < end) {
            val bucketEnd = minOf(end, hourStart(t) + HOUR_MS)
            val safeEnd = if (bucketEnd <= t) end else bucketEnd // guard against odd tz transitions
            hourly[hourOf(t)] += safeEnd - t
            t = safeEnd
        }
    }

    private const val HOUR_MS = 60L * 60 * 1000
}

fun formatDuration(ms: Long): String {
    val totalMinutes = (ms.coerceAtLeast(0) + 30_000) / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        totalMinutes == 0L && ms > 0 -> "<1m"
        else -> "${m}m"
    }
}
