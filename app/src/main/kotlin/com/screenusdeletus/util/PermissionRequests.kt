package com.screenusdeletus.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Returns an action that opens the system "Activate device admin app" screen. */
@Composable
fun rememberDeviceAdminRequest(onResult: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult() }
    return {
        try {
            launcher.launch(Permissions.deviceAdminIntent(context))
        } catch (e: ActivityNotFoundException) {
            Permissions.appDetails(context)
        }
    }
}

/**
 * Returns an action that asks for notification permission. If Android won't show the prompt
 * (already denied, or notifications switched off for the app), it opens the app's notification
 * settings instead.
 */
@Composable
fun rememberNotificationRequest(onResult: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted || !Permissions.notificationsEnabled(context)) Permissions.notificationSettings(context)
        onResult()
    }
    return {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else Permissions.notificationSettings(context)
    }
}
