package com.screenusdeletus.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.monitoring.GuardService
import com.screenusdeletus.security.Keys
import com.screenusdeletus.util.Time
import java.util.concurrent.TimeUnit

object Workers {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "integrity",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<IntegrityWorker>(15, TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "rollover",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyRolloverWorker>(3, TimeUnit.HOURS).build(),
        )
    }
}

/** Safety net: re-runs protection checks and restarts the guard service if it was killed. */
class IntegrityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = ScreenusDeletusApp.container(applicationContext)
        if (!c.profile.onboardingComplete) return Result.success()
        c.tamper.check()
        GuardService.start(applicationContext)
        return Result.success()
    }
}

/**
 * Records finished days (streaks, compliance), resets the weekly commitment counter and sends the
 * guardian's weekly report.
 */
class DailyRolloverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = ScreenusDeletusApp.container(applicationContext)
        if (!c.profile.onboardingComplete) return Result.success()
        c.stats.rollOverCompletedDays()
        c.rules.invalidate() // promotes rule changes whose cooling-off has elapsed

        val week = Time.weekNumber()
        val lastWeek = c.prefs.long(Keys.LAST_WEEKLY_REPORT_WEEK, -1)
        if (lastWeek != week) {
            c.prefs.put(Keys.LAST_WEEKLY_REPORT_WEEK, week)
            if (lastWeek != -1L) {
                c.commitment.newWeek()
                val g = c.guardian.guardian
                if (g != null && g.weeklyReportDelivery) {
                    c.guardian.notify("Weekly report", c.stats.weeklyReport())
                }
            }
        }
        return Result.success()
    }
}
