package com.screenusdeletus.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.screenusdeletus.ScreenusDeletusApp
import kotlinx.coroutines.launch

/** Boot, app update and clock changes. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = ScreenusDeletusApp.container(context)
        val pending = goAsync()
        c.scope.launch {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> c.tamper.onBoot()
                    Intent.ACTION_MY_PACKAGE_REPLACED -> c.tamper.check(forceIntegrity = true)
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                        c.usage.invalidate()
                        c.tamper.check()
                    }
                }
            } finally {
                pending.finish()
            }
        }
        if (c.profile.onboardingComplete) GuardService.start(context)
    }
}
