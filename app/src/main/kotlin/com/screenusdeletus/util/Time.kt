package com.screenusdeletus.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object Time {
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun now(): Long = System.currentTimeMillis()

    fun today(): LocalDate = LocalDate.now(zone)

    fun epochDay(millis: Long = now()): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun dayStart(epochDay: Long): Long = LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayEnd(epochDay: Long): Long = dayStart(epochDay + 1)

    fun startOfToday(): Long = dayStart(epochDay())

    fun weekStart(millis: Long = now()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun weekNumber(millis: Long = now()): Long = epochDay(weekStart(millis)) / 7

    private fun zdt(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)

    fun minuteOfDay(millis: Long = now()): Int = zdt(millis).let { it.hour * 60 + it.minute }

    fun isoDayOfWeek(millis: Long = now()): Int = zdt(millis).dayOfWeek.value

    fun hourOf(millis: Long): Int = zdt(millis).hour

    fun hourStart(millis: Long): Long = zdt(millis).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()

    fun formatClock(millis: Long): String = "%02d:%02d".format(zdt(millis).hour, zdt(millis).minute)

    fun formatDateTime(millis: Long): String {
        val z = zdt(millis)
        return "%d-%02d-%02d %02d:%02d".format(z.year, z.monthValue, z.dayOfMonth, z.hour, z.minute)
    }

    fun shortDay(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())

    fun formatCountdown(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
