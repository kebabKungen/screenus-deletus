package com.screenusdeletus.core

enum class TamperType(val severity: Int, val label: String) {
    DEVICE_ADMIN_DISABLE_REQUESTED(2, "Tried to deactivate device admin"),
    DEVICE_ADMIN_DISABLED(6, "Device admin was deactivated"),
    ACCESSIBILITY_DISABLED(3, "Monitoring service was turned off"),
    USAGE_ACCESS_REVOKED(3, "Usage access was revoked"),
    SIGNATURE_MISMATCH(6, "App signature does not match"),
    FILE_INTEGRITY_FAILED(6, "App files were modified"),
    RULE_INTEGRITY_FAILED(6, "Stored rules were modified outside Screenus Deletus"),
    ROOT_DETECTED(2, "Root or hooking framework detected"),
    ADB_ENABLED(1, "USB debugging is enabled"),
    CLOCK_CHANGED(2, "System clock was changed"),
    FORCE_STOPPED(2, "Screenus Deletus was force-stopped or killed"),
    WRONG_PIN(1, "Wrong PIN entered"),
}

data class TamperEvent(val timestamp: Long, val type: TamperType)

data class ProtectionVerdict(
    val level: ProtectionLevel,
    /** When the current lockdown/restriction ends, or null if it lasts until the cause is fixed. */
    val until: Long?,
    val score: Int,
)

/**
 * Progressive lockdown: tamper events are scored over a rolling window. A higher score leads to
 * a stricter protection level and longer lockdowns. A protection that is still disabled keeps the
 * device restricted until it is re-enabled.
 */
object TamperPolicy {
    const val WINDOW_MS = 24 * 60 * 60 * 1000L
    const val RESTRICTED_SCORE = 3
    const val LOCKDOWN_SCORE = 6

    fun verdict(
        events: List<TamperEvent>,
        now: Long,
        protectionsCurrentlyDisabled: Boolean,
    ): ProtectionVerdict {
        val recent = events.filter { now - it.timestamp in 0..WINDOW_MS }
        val score = recent.sumOf { it.type.severity }
        val lastEvent = recent.maxOfOrNull { it.timestamp }

        val level = when {
            score >= LOCKDOWN_SCORE -> ProtectionLevel.LOCKDOWN
            score >= RESTRICTED_SCORE || protectionsCurrentlyDisabled -> ProtectionLevel.RESTRICTED
            else -> ProtectionLevel.NORMAL
        }

        val until = when {
            level == ProtectionLevel.NORMAL -> null
            protectionsCurrentlyDisabled -> null
            level == ProtectionLevel.LOCKDOWN && lastEvent != null -> lastEvent + lockdownDuration(score)
            lastEvent != null -> lastEvent + 2 * 60 * 60 * 1000L
            else -> null
        }

        // A lockdown that has run its course decays to RESTRICTED (or NORMAL) even inside the window.
        if (until != null && now >= until) {
            return ProtectionVerdict(
                if (protectionsCurrentlyDisabled) ProtectionLevel.RESTRICTED else ProtectionLevel.NORMAL,
                null,
                score,
            )
        }
        return ProtectionVerdict(level, until, score)
    }

    /** 2h at the lockdown threshold, doubling for every further 3 points, capped at 24h. */
    fun lockdownDuration(score: Int): Long {
        val steps = ((score - LOCKDOWN_SCORE) / 3).coerceIn(0, 4)
        return (2L shl steps).coerceAtMost(24) * 60 * 60 * 1000L
    }
}
