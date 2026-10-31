package com.screenusdeletus.monitoring

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.ProtectionLevel
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.util.Notifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service: keeps the process alive, runs protection checks every minute, keeps the
 * status notification current and — when the accessibility service is off — falls back to
 * polling the foreground app from usage stats (needs the "display over other apps" permission to
 * show the block screen from the background).
 */
class GuardService : LifecycleService() {

    private val container by lazy { ScreenusDeletusApp.container(this) }

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_GUARD, Notifications.guard(this, "Starting…"), type)

        lifecycleScope.launch {
            while (isActive) {
                runCatching { tick() }.onFailure { Log.w(TAG, "check failed", it) }
                delay(CHECK_INTERVAL_MS)
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (AppMonitoringService.running) continue
                if (!getSystemService(PowerManager::class.java).isInteractive) continue
                val pkg = container.usage.currentForegroundApp() ?: continue
                runCatching { container.enforcement.onForeground(pkg) }
            }
        }
    }

    private suspend fun tick() {
        val status = container.tamper.check()
        val today = container.usage.today(maxAgeMs = 30_000)
        val text = buildString {
            append("Today ${formatDuration(today.totalAppMs)} of ${formatDuration(container.profile.dailyGoalMs)}")
            when (status.level) {
                ProtectionLevel.LOCKDOWN -> append(" · LOCKDOWN")
                ProtectionLevel.RESTRICTED -> append(" · restricted mode")
                ProtectionLevel.NORMAL -> Unit
            }
            if (!status.allProtectionsOn) append(" · protection off")
        }
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(Notifications.ID_GUARD, Notifications.guard(this, text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    companion object {
        private const val TAG = "GuardService"
        private const val CHECK_INTERVAL_MS = 60_000L
        private const val POLL_INTERVAL_MS = 2_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
            } catch (e: Exception) {
                // Android 12+ refuses background starts unless we're exempt (battery optimisation
                // off, boot, accessibility…). The periodic worker will try again.
                if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) {
                    Log.w(TAG, "Foreground service start not allowed right now")
                } else {
                    Log.e(TAG, "Could not start guard service", e)
                }
            }
        }
    }
}
