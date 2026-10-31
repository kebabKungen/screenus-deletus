package com.screenusdeletus

import android.app.Application
import android.content.Context
import com.screenusdeletus.monitoring.GuardService
import com.screenusdeletus.util.Notifications
import com.screenusdeletus.work.Workers

class ScreenusDeletusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        Workers.schedule(this)
        if (container.profile.onboardingComplete) GuardService.start(this)
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as ScreenusDeletusApp).container
    }
}
