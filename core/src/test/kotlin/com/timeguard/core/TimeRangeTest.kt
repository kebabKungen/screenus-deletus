package com.timeguard.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeRangeTest {
    @Test fun `simple range`() {
        val r = TimeRange.parse("09:00-17:00")
        assertTrue(r.contains(9 * 60, 1))
        assertTrue(r.contains(16 * 60 + 59, 1))
        assertFalse(r.contains(17 * 60, 1))
        assertFalse(r.contains(8 * 60, 1))
    }

    @Test fun `wrapping range respects start day`() {
        val r = TimeRange.parse("22:00-07:00", days = setOf(5)) // Friday night
        assertTrue(r.contains(23 * 60, 5))
        assertTrue(r.contains(6 * 60, 6)) // Saturday early morning belongs to Friday's range
        assertFalse(r.contains(6 * 60, 5)) // Friday early morning belongs to Thursday's
        assertFalse(r.contains(23 * 60, 6))
    }

    @Test fun `end of day`() {
        val r = TimeRange.parse("20:00-24:00")
        assertTrue(r.contains(23 * 60 + 59, 2))
        assertFalse(r.wrapsMidnight)
    }

    @Test fun `weakening detection`() {
        val base = ScreenTimeRule("a", "A", setOf("x"), emptySet(), 60_000, listOf(TimeRange.parse("22:00-07:00")), EscalationLevel.HARD, true, 0)
        assertTrue(base.isWeakenedBy(base.copy(dailyLimitMs = 120_000)))
        assertTrue(base.isWeakenedBy(base.copy(escalationLevel = EscalationLevel.SOFT)))
        assertTrue(base.isWeakenedBy(base.copy(targetPackages = emptySet())))
        assertTrue(base.isWeakenedBy(base.copy(blockedHours = listOf(TimeRange.parse("23:00-07:00")))))
        assertFalse(base.isWeakenedBy(base.copy(dailyLimitMs = 30_000, targetPackages = setOf("x", "y"))))
        assertFalse(base.isWeakenedBy(base.copy(blockedHours = listOf(TimeRange.parse("21:00-07:00")))))
    }
}
