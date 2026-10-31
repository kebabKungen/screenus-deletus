package com.screenusdeletus.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.screenusdeletus.R
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.TamperType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Device admin keeps Screenus Deletus from being uninstalled until admin is deactivated. Android does
 * not let any app *refuse* deactivation, so we make it costly instead: the attempt is logged, the
 * guardian is told, and deactivation itself triggers lockdown.
 */
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        ScreenusDeletusApp.container(context).prefs.put(Keys.LAST_KNOWN_ADMIN, true)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        LockdownManager.logTamper(context, TamperType.DEVICE_ADMIN_DISABLE_REQUESTED, "Deactivation screen opened")
        return context.getString(R.string.device_admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        LockdownManager.enterLockdown(context, "Device admin disabled")
    }
}

object LockdownManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun logTamper(context: Context, type: TamperType, detail: String) {
        val c = ScreenusDeletusApp.container(context)
        scope.launch { c.tamper.record(type, detail) }
    }

    /** Records a lockdown-grade tamper event and refreshes all protection state. */
    fun enterLockdown(context: Context, reason: String) {
        val c = ScreenusDeletusApp.container(context)
        c.prefs.put(Keys.LAST_KNOWN_ADMIN, false)
        scope.launch {
            c.tamper.record(TamperType.DEVICE_ADMIN_DISABLED, reason)
            c.tamper.check()
        }
    }
}
