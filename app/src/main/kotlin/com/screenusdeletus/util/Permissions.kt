package com.screenusdeletus.util

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.screenusdeletus.security.GuardDeviceAdminReceiver

/** Intents that take the user to the system screen where each permission is granted. */
object Permissions {
    fun usageAccess(context: Context) = open(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun accessibility(context: Context) = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /**
     * The "Activate device admin app" screen finishes immediately if it is started as a new task,
     * so it must be launched from an activity for a result — see [rememberDeviceAdminRequest].
     */
    fun deviceAdminIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, GuardDeviceAdminReceiver::class.java))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Stops Screenus Deletus from being uninstalled on impulse. You can still turn it off — doing so is logged.",
            )

    fun notificationSettings(context: Context) = open(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun notificationsEnabled(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun overlay(context: Context) = open(
        context,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
    )

    @android.annotation.SuppressLint("BatteryLife")
    fun batteryOptimizations(context: Context) = open(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
    )

    fun appDetails(context: Context) = open(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    fun email(context: Context, to: String, subject: String, body: String): Boolean = open(
        context,
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body),
    )

    fun url(context: Context, url: String) = open(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        // Some OEMs lack the per-package screens; fall back to app details.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        false
    }
}
