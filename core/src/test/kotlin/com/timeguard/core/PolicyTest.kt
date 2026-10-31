package com.timeguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PolicyTest {
    private val hour = 3_600_000L

    @Test fun `streaks`() {
        fun d(day: Long, ok: Boolean) = DayResult(day, if (ok) 10 else 100, 50, 0, 0)
        val days = listOf(d(1, true), d(2, true), d(3, true), d(4, false), d(5, true), d(6, true))
        val s = StreakCalculator.compute(days, today = 7)
        assertEquals(2, s.currentStreak)
        assertEquals(3, s.longestStreak)
        assertEquals(6L, s.lastSuccessfulDay)
        assertEquals(listOf(4L), s.streakBrokenDays)
        assertEquals(0, StreakCalculator.compute(days, today = 9).currentStreak) // gap breaks streak
    }

    @Test fun `violations break success`() {
        assertFalse(DayResult(1, 10, 50, 1, 0).successful)
        assertEquals(100.0, StreakCalculator.complianceScore(listOf(DayResult(1, 10, 50, 0, 0))), 0.001)
        assertTrue(StreakCalculator.complianceScore(listOf(DayResult(1, 60, 50, 0, 0))) in 1.0..50.0)
    }

    @Test fun `unlock quota cooldown and wait`() {
        val s = UnlockSettings(maxUnlocksPerWeek = 2, cooldownMs = 4 * hour)
        val now = 100 * hour
        val e = UnlockPolicy.eligibility(s, emptyList(), now, 0, false, ProtectionLevel.NORMAL)
        assertEquals(s.baseWaitMs, (e as UnlockEligibility.Eligible).waitMs)

        val cooling = UnlockPolicy.eligibility(s, listOf(now - hour), now, 0, false, ProtectionLevel.NORMAL)
        assertTrue(cooling is UnlockEligibility.Denied)

        val second = UnlockPolicy.eligibility(s, listOf(now - 5 * hour), now, 0, false, ProtectionLevel.NORMAL)
        assertEquals(s.baseWaitMs * 2, (second as UnlockEligibility.Eligible).waitMs)

        val exhausted = UnlockPolicy.eligibility(s, listOf(now - 10 * hour, now - 5 * hour), now, 0, false, ProtectionLevel.NORMAL)
        assertTrue(exhausted is UnlockEligibility.Denied)

        val guardian = UnlockPolicy.eligibility(s, listOf(now - 10 * hour, now - 5 * hour), now, 0, true, ProtectionLevel.LOCKDOWN)
        assertTrue((guardian as UnlockEligibility.Eligible).requiresGuardian)

        assertTrue(UnlockPolicy.eligibility(s, emptyList(), now, 0, false, ProtectionLevel.LOCKDOWN) is UnlockEligibility.Denied)
        assertEquals(s.maxWaitMs, UnlockPolicy.waitFor(s, 20))
    }

    @Test fun `challenge`() {
        val c = UnlockChallenge.generate(Random(1))
        assertTrue(UnlockChallenge.verify(c, "  " + c.lowercase().replace(" ", "  ") + " "))
        assertFalse(UnlockChallenge.verify(c, c.dropLast(1)))
    }

    @Test fun `tamper escalation`() {
        val now = 50 * hour
        assertEquals(ProtectionLevel.NORMAL, TamperPolicy.verdict(emptyList(), now, false).level)
        assertEquals(ProtectionLevel.RESTRICTED, TamperPolicy.verdict(emptyList(), now, true).level)
        val one = listOf(TamperEvent(now - 1000, TamperType.ACCESSIBILITY_DISABLED))
        assertEquals(ProtectionLevel.RESTRICTED, TamperPolicy.verdict(one, now, false).level)
        val severe = listOf(TamperEvent(now - 1000, TamperType.DEVICE_ADMIN_DISABLED), TamperEvent(now - 500, TamperType.WRONG_PIN))
        val v = TamperPolicy.verdict(severe, now, false)
        assertEquals(ProtectionLevel.LOCKDOWN, v.level)
        assertEquals(now - 500 + 2 * hour, v.until)
        // after the lockdown period it decays
        assertEquals(ProtectionLevel.NORMAL, TamperPolicy.verdict(severe, now + 3 * hour, false).level)
        // old events fall out of the window
        assertEquals(ProtectionLevel.NORMAL, TamperPolicy.verdict(severe, now + 25 * hour, false).level)
        assertEquals(24 * hour, TamperPolicy.lockdownDuration(100))
    }

    @Test fun `commitment escalation`() {
        var c = CommitmentDevice(pledgedAmount = 10.0, basePenalty = 2.0, escalationMultiplier = 2.0, charityName = "x", charityUrl = "")
        val (c1, p1) = CommitmentLedger.recordViolation(c); c = c1
        val (c2, p2) = CommitmentLedger.recordViolation(c); c = c2
        val (c3, p3) = CommitmentLedger.recordViolation(c); c = c3
        assertEquals(2.0, p1, 0.0); assertEquals(4.0, p2, 0.0); assertEquals(4.0, p3, 0.0) // capped by pledge
        assertEquals(10.0, c.totalOwed, 0.0)
        assertEquals(0.0, CommitmentLedger.recordViolation(c).second, 0.0)
        c = CommitmentLedger.settle(c, 6.0)
        assertEquals(4.0, c.outstanding, 0.0)
        assertEquals(0, CommitmentLedger.newWeek(c).violationsThisWeek)
    }

    @Test fun `habit suggestions fit time and preferences`() {
        repeat(20) {
            val a = HabitSuggester.suggest(5, listOf("exercise"), random = Random(it))
            assertTrue(a.minutes <= 5)
            assertEquals("exercise", a.tag)
        }
        assertTrue(HabitSuggester.suggest(0, emptyList()).minutes <= 2)
    }

    @Test fun `insights`() {
        val hourly = LongArray(24).also { it[23] = 10 * hour; it[12] = hour }
        val triggers = (1..4).map { TriggerLog(it.toLong(), "a", 23, 1, "Bored", 3, 8, proceeded = it % 2 == 0) }
        val out = InsightsEngine.generate(
            InsightInput(hourly, listOf(100, 100, 100, 50, 50, 50), 60, triggers, StreakData(4, 6, 1, emptyList()), 2, 3, listOf("a" to 10L, "b" to 1L), 90),
        )
        assertTrue(out.any { it.contains("23:00") })
        assertTrue(out.any { it.contains("late at night") })
        assertTrue(out.any { it.contains("Down 50%") })
        assertTrue(out.any { it.contains("Bored") })
        assertTrue(out.any { it.contains("stressed") })
        assertTrue(out.any { it.contains("4-day streak") })
        assertTrue(out.any { it.contains("90 pickups") })
    }
}
