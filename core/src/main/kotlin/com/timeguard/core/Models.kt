package com.timeguard.core

/**
 * How aggressively a rule is enforced once it is violated.
 */
enum class EscalationLevel {
    /** Warnings and notifications only. */
    SOFT,

    /** Friction: interstitial screens with a forced, growing delay before the app may continue. */
    MEDIUM,

    /** Complete blocking. */
    HARD;

    fun stricter(): EscalationLevel = entries[minOf(ordinal + 1, entries.size - 1)]
}

enum class AppCategory(val label: String) {
    SOCIAL_MEDIA("Social media"),
    ENTERTAINMENT("Entertainment"),
    GAMES("Games"),
    NEWS("News"),
    SHOPPING("Shopping"),
    COMMUNICATION("Communication"),
    PRODUCTIVITY("Productivity"),
    EDUCATION("Education"),
    HEALTH("Health & fitness"),
    UTILITIES("Utilities"),
    OTHER("Other"),
}

/**
 * A window of the day expressed in minutes since local midnight. `end` may be smaller than
 * `start`, in which case the range wraps past midnight (e.g. 22:00–07:00).
 *
 * [days] uses ISO day numbers (1 = Monday … 7 = Sunday). Empty means every day. For a range that
 * wraps midnight, the day refers to the day the range *starts* on.
 */
data class TimeRange(
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<Int> = emptySet(),
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range: $startMinute" }
        require(endMinute in 0..MINUTES_PER_DAY) { "endMinute out of range: $endMinute" }
        require(days.all { it in 1..7 }) { "days must be ISO day numbers 1..7" }
    }

    val wrapsMidnight: Boolean get() = endMinute < startMinute

    /**
     * @param minuteOfDay minutes since local midnight, 0..1439
     * @param isoDayOfWeek 1 = Monday … 7 = Sunday
     */
    fun contains(minuteOfDay: Int, isoDayOfWeek: Int): Boolean {
        if (startMinute == endMinute) return false
        return if (!wrapsMidnight) {
            dayMatches(isoDayOfWeek) && minuteOfDay >= startMinute && minuteOfDay < endMinute
        } else {
            val previousDay = if (isoDayOfWeek == 1) 7 else isoDayOfWeek - 1
            (minuteOfDay >= startMinute && dayMatches(isoDayOfWeek)) ||
                (minuteOfDay < endMinute && dayMatches(previousDay))
        }
    }

    private fun dayMatches(day: Int) = days.isEmpty() || day in days

    fun format(): String = "${formatMinute(startMinute)}–${formatMinute(endMinute)}"

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun formatMinute(m: Int): String = "%02d:%02d".format((m / 60) % 24, m % 60)

        /** Parses `HH:mm-HH:mm`. */
        fun parse(text: String, days: Set<Int> = emptySet()): TimeRange {
            val parts = text.split('-', '–').map { it.trim() }
            require(parts.size == 2) { "Expected HH:mm-HH:mm" }
            return TimeRange(parseMinute(parts[0]), parseMinute(parts[1]), days)
        }

        fun parseMinute(text: String): Int {
            val (h, m) = text.split(':').map { it.trim().toInt() }
            require(h in 0..24 && m in 0..59) { "Invalid time: $text" }
            return (h * 60 + m).coerceAtMost(MINUTES_PER_DAY)
        }
    }
}

/**
 * A screen-time rule. [integrityTag] is a MAC over [canonicalForm] computed by the app's crypto
 * layer; rules whose tag does not verify are treated as tampered with.
 */
data class ScreenTimeRule(
    val id: String,
    val name: String,
    val targetPackages: Set<String>,
    val targetCategories: Set<AppCategory> = emptySet(),
    /** Allowed foreground time per day; [NO_LIMIT] for none, 0 to block completely. */
    val dailyLimitMs: Long,
    val blockedHours: List<TimeRange> = emptyList(),
    val escalationLevel: EscalationLevel = EscalationLevel.MEDIUM,
    val enabled: Boolean = true,
    val createdAt: Long,
    /** Changes that make a rule *weaker* only take effect after this timestamp. */
    val pendingWeakeningAppliesAt: Long? = null,
    val integrityTag: String = "",
) {
    fun appliesTo(packageName: String, category: AppCategory?): Boolean =
        enabled && (packageName in targetPackages || (category != null && category in targetCategories))

    /** Deterministic serialization used as the input to the integrity MAC. */
    fun canonicalForm(): String = buildString {
        append(id).append('|')
        append(name).append('|')
        append(targetPackages.sorted().joinToString(",")).append('|')
        append(targetCategories.map { it.name }.sorted().joinToString(",")).append('|')
        append(dailyLimitMs).append('|')
        append(blockedHours.joinToString(",") { "${it.startMinute}-${it.endMinute}:${it.days.sorted().joinToString("")}" })
        append('|').append(escalationLevel.name)
        append('|').append(enabled)
        append('|').append(createdAt)
        append('|').append(pendingWeakeningAppliesAt ?: -1)
    }

    /**
     * True when [other] would loosen restrictions compared to this rule. Loosening changes are
     * subject to a cooling-off delay so a moment of weakness can't immediately undo a commitment.
     */
    fun isWeakenedBy(other: ScreenTimeRule): Boolean =
        other.dailyLimitMs > dailyLimitMs ||
            other.escalationLevel < escalationLevel ||
            (enabled && !other.enabled) ||
            !other.targetPackages.containsAll(targetPackages) ||
            !other.targetCategories.containsAll(targetCategories) ||
            blockedMinutesPerWeek(other.blockedHours) < blockedMinutesPerWeek(blockedHours)

    private fun blockedMinutesPerWeek(ranges: List<TimeRange>): Int = ranges.sumOf { r ->
        val len = if (r.wrapsMidnight) TimeRange.MINUTES_PER_DAY - r.startMinute + r.endMinute else r.endMinute - r.startMinute
        len * (if (r.days.isEmpty()) 7 else r.days.size)
    }

    companion object {
        /** [dailyLimitMs] value meaning "no daily limit" (blocked hours only). */
        const val NO_LIMIT = -1L
    }
}

/** Global protection state that can tighten enforcement. */
enum class ProtectionLevel {
    NORMAL,

    /** A protection (e.g. the accessibility service) was turned off — every rule is one level stricter. */
    RESTRICTED,

    /** Repeated or severe tampering — every rule is enforced as HARD. */
    LOCKDOWN,
}

data class EnforcementContext(
    val nowMillis: Long,
    val minuteOfDay: Int,
    val isoDayOfWeek: Int,
    val protectionLevel: ProtectionLevel = ProtectionLevel.NORMAL,
    /** Root / hooking frameworks detected: rules are enforced one level stricter. */
    val deviceCompromised: Boolean = false,
    /** Emergency unlock in effect until this timestamp. */
    val unlockedUntil: Long = 0L,
    /** Packages granted a temporary pass after completing an interstitial, mapped to expiry. */
    val interstitialPasses: Map<String, Long> = emptyMap(),
)

sealed interface Decision {
    val severity: Int

    data object Allow : Decision {
        override val severity = 0
    }

    data class Warn(val ruleId: String, val remainingMs: Long, val message: String) : Decision {
        override val severity = 1
    }

    data class Interstitial(val ruleId: String, val delaySeconds: Int, val reason: String) : Decision {
        override val severity = 2
    }

    data class Block(val ruleId: String, val reason: String) : Decision {
        override val severity = 3
    }
}
