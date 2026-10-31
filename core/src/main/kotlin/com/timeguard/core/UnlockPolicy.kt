package com.timeguard.core

data class UnlockSettings(
    val maxUnlocksPerWeek: Int = 3,
    /** Minimum time between two emergency unlocks. */
    val cooldownMs: Long = 4 * HOUR,
    /** Mandatory wait before the first unlock of the week is granted. */
    val baseWaitMs: Long = 2 * MINUTE,
    /** Every further unlock in the same week multiplies the wait by this factor. */
    val waitMultiplier: Double = 2.0,
    val maxWaitMs: Long = 30 * MINUTE,
    /** How long an unlock lasts. */
    val unlockDurationMs: Long = 15 * MINUTE,
    val requiresChallenge: Boolean = true,
    /** When a guardian is configured, require their PIN instead of allowing self-service. */
    val requireGuardianApproval: Boolean = false,
) {
    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}

sealed interface UnlockEligibility {
    /** The user may start the unlock flow; they will have to wait [waitMs] and solve a challenge. */
    data class Eligible(val waitMs: Long, val requiresChallenge: Boolean, val requiresGuardian: Boolean) :
        UnlockEligibility

    data class Denied(val reason: String, val retryAt: Long?) : UnlockEligibility
}

object UnlockPolicy {
    /**
     * @param unlockTimestamps when previous emergency unlocks were granted
     * @param weekStart start of the current week (local time)
     */
    fun eligibility(
        settings: UnlockSettings,
        unlockTimestamps: List<Long>,
        now: Long,
        weekStart: Long,
        guardianConfigured: Boolean,
        protectionLevel: ProtectionLevel,
    ): UnlockEligibility {
        val thisWeek = unlockTimestamps.count { it >= weekStart }
        val requiresGuardian = guardianConfigured &&
            (settings.requireGuardianApproval || protectionLevel == ProtectionLevel.LOCKDOWN)

        if (protectionLevel == ProtectionLevel.LOCKDOWN && !guardianConfigured) {
            return UnlockEligibility.Denied(
                "TimeGuard is in lockdown after tampering was detected. Emergency unlocks are unavailable until it ends.",
                null,
            )
        }
        if (thisWeek >= settings.maxUnlocksPerWeek && !requiresGuardian) {
            return UnlockEligibility.Denied(
                "You have used all ${settings.maxUnlocksPerWeek} emergency unlocks this week.",
                weekStart + 7 * 24 * UnlockSettings.HOUR,
            )
        }
        val last = unlockTimestamps.maxOrNull()
        if (last != null && now - last < settings.cooldownMs && !requiresGuardian) {
            return UnlockEligibility.Denied("Emergency unlocks are cooling down.", last + settings.cooldownMs)
        }
        return UnlockEligibility.Eligible(
            waitMs = waitFor(settings, thisWeek),
            requiresChallenge = settings.requiresChallenge,
            requiresGuardian = requiresGuardian,
        )
    }

    fun waitFor(settings: UnlockSettings, unlocksAlreadyThisWeek: Int): Long {
        var wait = settings.baseWaitMs.toDouble()
        repeat(unlocksAlreadyThisWeek) { wait *= settings.waitMultiplier }
        return wait.toLong().coerceAtMost(settings.maxWaitMs)
    }
}

/** Typing challenge: forces a deliberate, slow action rather than a reflexive tap. */
object UnlockChallenge {
    private val phrases = listOf(
        "I am choosing to spend my attention on this on purpose",
        "This unlock is a deliberate choice and I accept the cost",
        "I will close this app as soon as I have done what I need",
        "My future self is counting on the promise I made today",
        "I know exactly why I need this and it cannot wait",
    )

    fun generate(random: kotlin.random.Random = kotlin.random.Random.Default): String {
        val phrase = phrases.random(random)
        val code = (1..4).map { ('A'..'Z').random(random) }.joinToString("")
        return "$phrase $code"
    }

    /** Exact match, ignoring case and surrounding whitespace. No partial credit. */
    fun verify(expected: String, typed: String): Boolean =
        expected.trim().equals(typed.trim().replace(Regex("\\s+"), " "), ignoreCase = true)
}
