package com.timeguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private val engine = RuleEngine()
    private val minute = 60_000L

    private fun rule(
        level: EscalationLevel = EscalationLevel.HARD,
        limit: Long = 30 * minute,
        hours: List<TimeRange> = emptyList(),
        packages: Set<String> = setOf("com.social"),
        categories: Set<AppCategory> = emptySet(),
        id: String = "r1",
    ) = ScreenTimeRule(
        id = id, name = "Social", targetPackages = packages, targetCategories = categories,
        dailyLimitMs = limit, blockedHours = hours, escalationLevel = level, createdAt = 0,
    )

    private fun ctx(
        minuteOfDay: Int = 12 * 60,
        protection: ProtectionLevel = ProtectionLevel.NORMAL,
        compromised: Boolean = false,
        unlockedUntil: Long = 0,
        passes: Map<String, Long> = emptyMap(),
    ) = EnforcementContext(1_000_000, minuteOfDay, 3, protection, compromised, unlockedUntil, passes)

    private fun eval(rules: List<ScreenTimeRule>, used: Long, c: EnforcementContext = ctx(), pkg: String = "com.social", cat: AppCategory? = null) =
        engine.evaluate(pkg, cat, rules, c, { used })

    @Test fun `allows under limit`() = assertEquals(Decision.Allow, eval(listOf(rule()), 5 * minute))

    @Test fun `unrelated package is allowed`() =
        assertEquals(Decision.Allow, eval(listOf(rule()), 99 * minute, pkg = "com.work"))

    @Test fun `warns at eighty percent`() {
        val d = eval(listOf(rule()), 25 * minute)
        assertTrue(d is Decision.Warn)
        assertEquals(5 * minute, (d as Decision.Warn).remainingMs)
    }

    @Test fun `hard blocks over limit`() = assertTrue(eval(listOf(rule()), 30 * minute) is Decision.Block)

    @Test fun `medium shows interstitial and pass allows`() {
        assertTrue(eval(listOf(rule(EscalationLevel.MEDIUM)), 31 * minute) is Decision.Interstitial)
        val withPass = ctx(passes = mapOf("com.social" to 2_000_000L))
        assertEquals(Decision.Allow, eval(listOf(rule(EscalationLevel.MEDIUM)), 31 * minute, withPass))
    }

    @Test fun `soft only warns`() = assertTrue(eval(listOf(rule(EscalationLevel.SOFT)), 31 * minute) is Decision.Warn)

    @Test fun `blocked hours block even with no usage`() {
        val r = rule(limit = ScreenTimeRule.NO_LIMIT, hours = listOf(TimeRange.parse("09:00-17:00")))
        assertTrue(eval(listOf(r), 0) is Decision.Block)
        assertEquals(Decision.Allow, eval(listOf(r), 0, ctx(minuteOfDay = 18 * 60)))
    }

    @Test fun `zero limit always blocks`() = assertTrue(eval(listOf(rule(limit = 0)), 0) is Decision.Block)

    @Test fun `lockdown escalates soft to hard`() =
        assertTrue(eval(listOf(rule(EscalationLevel.SOFT)), 31 * minute, ctx(protection = ProtectionLevel.LOCKDOWN)) is Decision.Block)

    @Test fun `restricted and compromised each escalate one level`() {
        assertTrue(eval(listOf(rule(EscalationLevel.SOFT)), 31 * minute, ctx(protection = ProtectionLevel.RESTRICTED)) is Decision.Interstitial)
        assertTrue(eval(listOf(rule(EscalationLevel.MEDIUM)), 31 * minute, ctx(compromised = true)) is Decision.Block)
    }

    @Test fun `emergency unlock allows`() =
        assertEquals(Decision.Allow, eval(listOf(rule()), 99 * minute, ctx(unlockedUntil = 2_000_000)))

    @Test fun `strictest of multiple rules wins`() {
        val soft = rule(EscalationLevel.SOFT, id = "a")
        val hard = rule(EscalationLevel.HARD, limit = 60 * minute, id = "b", hours = listOf(TimeRange.parse("11:00-13:00")))
        assertTrue(eval(listOf(soft, hard), 40 * minute) is Decision.Block)
    }

    @Test fun `category rule applies`() {
        val r = rule(packages = emptySet(), categories = setOf(AppCategory.GAMES))
        assertTrue(eval(listOf(r), 31 * minute, pkg = "com.game", cat = AppCategory.GAMES) is Decision.Block)
    }

    @Test fun `dialer is never blocked`() =
        assertEquals(Decision.Allow, eval(listOf(rule(limit = 0, packages = setOf("com.android.dialer"))), 0, pkg = "com.android.dialer"))

    @Test fun `interstitial delay grows and caps`() {
        assertEquals(15, engine.interstitialDelay(0))
        assertEquals(35, engine.interstitialDelay(2))
        assertEquals(180, engine.interstitialDelay(100))
    }
}
