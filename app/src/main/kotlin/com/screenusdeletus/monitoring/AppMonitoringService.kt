package com.screenusdeletus.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.security.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time app monitoring. The service is told immediately when a different app's window comes
 * to the foreground; it only uses the event's package name (window content is not requested —
 * see accessibility_service_config.xml).
 */
class AppMonitoringService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val container by lazy { ScreenusDeletusApp.container(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        container.prefs.put(Keys.LAST_KNOWN_ACCESSIBILITY, true)
        GuardService.start(this)
        scope.launch {
            // Re-check periodically so a limit that runs out mid-session still applies.
            while (isActive) {
                delay(TICK_MS)
                if (!isInteractive()) continue
                val pkg = container.usage.currentForegroundApp() ?: container.enforcement.currentForeground ?: continue
                runCatching { container.enforcement.onForeground(pkg) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == currentInputMethodPackage()) return
        scope.launch { runCatching { container.enforcement.onForeground(pkg) } }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        // The user may have just turned the service off; let the tamper monitor notice.
        container.scope.launch {
            delay(1_000)
            container.tamper.check()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun isInteractive(): Boolean = getSystemService(PowerManager::class.java).isInteractive

    private fun currentInputMethodPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    companion object {
        private const val TICK_MS = 15_000L

        @Volatile
        var running: Boolean = false
            private set
    }
}
