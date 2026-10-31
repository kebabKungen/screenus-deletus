package com.screenusdeletus.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.screenusdeletus.R
import com.screenusdeletus.ui.MainActivity

object Notifications {
    const val CHANNEL_GUARD = "guard"
    const val CHANNEL_WARNINGS = "warnings"
    const val CHANNEL_ALERTS = "alerts"

    const val ID_GUARD = 1
    const val ID_PROTECTION = 2
    const val ID_GUARDIAN_OUTBOX = 3
    const val ID_COMMITMENT = 4
    private const val ID_WARNING_BASE = 1000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_GUARD, "Protection status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Persistent notification while Screenus Deletus is protecting you"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_WARNINGS, "Limit warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Heads-up when you're close to or over a limit"
                },
                NotificationChannel(CHANNEL_ALERTS, "Security alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Protections disabled, tamper attempts and guardian messages"
                },
            ),
        )
    }

    fun openAppIntent(context: Context, route: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route) }
        return PendingIntent.getActivity(
            context, route.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun guard(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Screenus Deletus is active")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent(context))
            .build()

    fun warn(context: Context, key: String, title: String, text: String) =
        post(context, ID_WARNING_BASE + (key.hashCode() and 0xFFFF), CHANNEL_WARNINGS, title, text, null)

    fun alert(context: Context, id: Int, title: String, text: String, route: String? = "security") =
        post(context, id, CHANNEL_ALERTS, title, text, route)

    private fun post(context: Context, id: Int, channel: String, title: String, text: String, route: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context, route))
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
