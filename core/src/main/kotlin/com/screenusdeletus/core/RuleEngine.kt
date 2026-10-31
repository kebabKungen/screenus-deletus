package com.screenusdeletus.core

/**
 * Pure decision logic: given the rules, today's usage and the current protection state, decide
 * what should happen when [packageName] is in the foreground.
 *
 * The engine never touches Android APIs so it can be unit tested exhaustively.
 */
class RuleEngine(
    /** Fraction of the daily limit at which a warning is shown. */
    private val warningThreshold: Double = 0.8,
    /** Base forced delay for MEDIUM interstitials, in seconds. */
    private val baseInterstitialSeconds: Int = 15,
    /** Extra seconds added for every interstitial already sat through today. */
    private val interstitialStepSeconds: Int = 10,
    private val maxInterstitialSeconds: Int = 180,
) {

    /**
     * @param usageForRule total foreground time today across everything the rule targets
     * @param interstitialsToday how many interstitials were already shown today for a rule
     */
    fun evaluate(
        packageName: String,
        category: AppCategory?,
        rules: List<ScreenTimeRule>,
        ctx: EnforcementContext,
        usageForRule: (ScreenTimeRule) -> Long,
        interstitialsToday: (ScreenTimeRule) -> Int = { 0 },
    ): Decision {
        if (packageName in ALWAYS_ALLOWED) return Decision.Allow
        if (ctx.unlockedUntil > ctx.nowMillis) return Decision.Allow

        val applicable = rules.filter { it.appliesTo(packageName, category) }
        if (applicable.isEmpty()) return Decision.Allow

        val hasPass = (ctx.interstitialPasses[packageName] ?: 0L) > ctx.nowMillis

        return applicable
            .map { rule -> evaluateRule(rule, ctx, usageForRule(rule), interstitialsToday(rule), hasPass) }
            .maxBy { it.severity }
    }

    private fun evaluateRule(
        rule: ScreenTimeRule,
        ctx: EnforcementContext,
        usedMs: Long,
        interstitialsToday: Int,
        hasPass: Boolean,
    ): Decision {
        val level = effectiveLevel(rule.escalationLevel, ctx)
        val blockedRange = rule.blockedHours.firstOrNull { it.contains(ctx.minuteOfDay, ctx.isoDayOfWeek) }
        val overLimit = rule.dailyLimitMs in 0..usedMs

        val reason = when {
            blockedRange != null -> "${rule.name} is blocked during focus hours (${blockedRange.format()})"
            overLimit -> "Daily limit of ${formatDuration(rule.dailyLimitMs)} reached for ${rule.name}"
            else -> null
        }

        if (reason != null) {
            return when (level) {
                EscalationLevel.HARD -> Decision.Block(rule.id, reason)
                EscalationLevel.MEDIUM ->
                    if (hasPass) Decision.Allow
                    else Decision.Interstitial(rule.id, interstitialDelay(interstitialsToday), reason)
                EscalationLevel.SOFT -> Decision.Warn(rule.id, 0, reason)
            }
        }

        if (rule.dailyLimitMs > 0 && usedMs >= rule.dailyLimitMs * warningThreshold) {
            val remaining = rule.dailyLimitMs - usedMs
            return Decision.Warn(
                rule.id,
                remaining,
                "${formatDuration(remaining)} left today for ${rule.name}",
            )
        }
        return Decision.Allow
    }

    fun effectiveLevel(base: EscalationLevel, ctx: EnforcementContext): EscalationLevel {
        var level = base
        if (ctx.deviceCompromised) level = level.stricter()
        when (ctx.protectionLevel) {
            ProtectionLevel.NORMAL -> Unit
            ProtectionLevel.RESTRICTED -> level = level.stricter()
            ProtectionLevel.LOCKDOWN -> level = EscalationLevel.HARD
        }
        return level
    }

    fun interstitialDelay(alreadyShownToday: Int): Int =
        (baseInterstitialSeconds + alreadyShownToday * interstitialStepSeconds).coerceAtMost(maxInterstitialSeconds)

    companion object {
        /**
         * Never block these, whatever the rules say: the phone must stay usable for emergencies,
         * and blocking the launcher or Screenus Deletus itself would trap the user.
         */
        val ALWAYS_ALLOWED: Set<String> = setOf(
            "com.screenusdeletus",
            "com.android.systemui",
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.android.settings.intelligence",
        )
    }
}
