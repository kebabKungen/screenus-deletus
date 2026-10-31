package com.screenusdeletus.core

/** Outcome of one calendar day. [epochDay] is `LocalDate.toEpochDay()`. */
data class DayResult(
    val epochDay: Long,
    val usageMs: Long,
    val goalMs: Long,
    val violations: Int,
    val emergencyUnlocks: Int,
) {
    /** A day counts towards the streak when no rule was violated and the overall goal was met. */
    val successful: Boolean get() = violations == 0 && (goalMs <= 0 || usageMs <= goalMs)
}

data class StreakData(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastSuccessfulDay: Long?,
    val streakBrokenDays: List<Long>,
)

object StreakCalculator {
    /**
     * @param days results for completed days (today excluded – it is still in progress)
     * @param today the current epoch day
     */
    fun compute(days: List<DayResult>, today: Long): StreakData {
        val byDay = days.associateBy { it.epochDay }
        val sorted = days.map { it.epochDay }.distinct().sorted()

        var longest = 0
        var run = 0
        var prev: Long? = null
        val broken = mutableListOf<Long>()
        for (d in sorted) {
            val ok = byDay.getValue(d).successful
            val contiguous = prev != null && d == prev + 1
            run = when {
                ok && contiguous -> run + 1
                ok -> 1
                else -> {
                    if (run > 0) broken += d
                    0
                }
            }
            longest = maxOf(longest, run)
            prev = d
        }

        // The current streak counts back from yesterday; a missing day breaks it.
        var current = 0
        var cursor = today - 1
        while (byDay[cursor]?.successful == true) {
            current++
            cursor--
        }

        return StreakData(
            currentStreak = current,
            longestStreak = maxOf(longest, current),
            lastSuccessfulDay = sorted.lastOrNull { byDay.getValue(it).successful },
            streakBrokenDays = broken,
        )
    }

    /**
     * Compliance score, 0–100: the share of days on target, with partial credit for days that went
     * over by a small margin.
     */
    fun complianceScore(days: List<DayResult>): Double {
        if (days.isEmpty()) return 100.0
        val total = days.sumOf { d ->
            when {
                d.successful -> 1.0
                d.goalMs <= 0 -> if (d.violations == 0) 1.0 else 0.0
                else -> {
                    val overRatio = (d.usageMs - d.goalMs).toDouble() / d.goalMs
                    val usagePart = (1.0 - overRatio).coerceIn(0.0, 1.0)
                    val violationPenalty = (d.violations * 0.25).coerceAtMost(1.0)
                    (usagePart - violationPenalty).coerceAtLeast(0.0) * 0.5
                }
            }
        }
        return (total / days.size * 100.0).coerceIn(0.0, 100.0)
    }
}
