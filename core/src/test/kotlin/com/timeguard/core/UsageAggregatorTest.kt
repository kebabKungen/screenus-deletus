package com.timeguard.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAggregatorTest {
    private val h = 3_600_000L
    private val m = 60_000L
    private fun hourOf(t: Long) = ((t / h) % 24).toInt()
    private fun hourStart(t: Long) = t - t % h

    private fun fg(t: Long, p: String) = RawUsageEvent(t, p, RawUsageEvent.Type.FOREGROUND)
    private fun bg(t: Long, p: String) = RawUsageEvent(t, p, RawUsageEvent.Type.BACKGROUND)
    private fun off(t: Long) = RawUsageEvent(t, "", RawUsageEvent.Type.SCREEN_OFF)
    private fun on(t: Long) = RawUsageEvent(t, "", RawUsageEvent.Type.SCREEN_ON)

    private fun agg(events: List<RawUsageEvent>, end: Long = 24 * h, initial: String? = null) =
        UsageAggregator.aggregate(events, 0, end, ::hourOf, ::hourStart, setOf("launcher"), initial)

    @Test fun `basic session`() {
        val s = agg(listOf(on(10 * h), fg(10 * h, "a"), bg(10 * h + 30 * m, "a"), off(11 * h)))
        assertEquals(30 * m, s.perPackageMs["a"])
        assertEquals(h, s.screenOnMs)
        assertEquals(1, s.pickups)
        assertEquals(1, s.launches["a"])
    }

    @Test fun `missing background closed by next foreground`() {
        val s = agg(listOf(fg(0, "a"), fg(10 * m, "b"), bg(15 * m, "b")))
        assertEquals(10 * m, s.perPackageMs["a"])
        assertEquals(5 * m, s.perPackageMs["b"])
    }

    @Test fun `duplicate foreground ignored`() {
        val s = agg(listOf(fg(0, "a"), fg(5 * m, "a"), bg(10 * m, "a")))
        assertEquals(10 * m, s.perPackageMs["a"])
        assertEquals(1, s.launches["a"])
    }

    @Test fun `open session runs to window end`() {
        val s = agg(listOf(fg(0, "a")), end = 20 * m)
        assertEquals(20 * m, s.perPackageMs["a"])
    }

    @Test fun `session spanning hours is split`() {
        val s = agg(listOf(fg(10 * h + 50 * m, "a"), bg(11 * h + 20 * m, "a")))
        assertEquals(10 * m, s.hourlyMs[10])
        assertEquals(20 * m, s.hourlyMs[11])
    }

    @Test fun `initial foreground counted from window start`() {
        val s = agg(listOf(bg(5 * m, "a")), initial = "a")
        assertEquals(5 * m, s.perPackageMs["a"])
        assertEquals(null, s.launches["a"])
    }

    @Test fun `ignored packages and screen off`() {
        val s = agg(listOf(fg(0, "launcher"), fg(m, "a"), off(3 * m), fg(10 * m, "a"), bg(11 * m, "a")))
        assertEquals(null, s.perPackageMs["launcher"])
        assertEquals(3 * m, s.perPackageMs["a"])
    }

    @Test fun `format duration`() {
        assertEquals("1h 5m", formatDuration(65 * m))
        assertEquals("2h", formatDuration(120 * m))
        assertEquals("<1m", formatDuration(10_000))
        assertEquals("0m", formatDuration(0))
    }
}
