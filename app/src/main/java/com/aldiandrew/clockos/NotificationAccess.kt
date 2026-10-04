package com.aldiandrew.clockos

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

fun hasClockNotificationAccess(
    context: Context
): Boolean {
    val component =
        ComponentName(
            context,
            ClockNotificationListener::class.java
        )

    if (Build.VERSION.SDK_INT >= 27) {
        try {
            return context
                .getSystemService(
                    NotificationManager::class.java
                )
                ?.isNotificationListenerAccessGranted(
                    component
                ) == true
        } catch (_: Throwable) {
        }
    }

    val enabled =
        try {
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
        } catch (_: Throwable) {
            null
        }

    return enabled
        ?.split(":")
        ?.any {
            it == component.flattenToString() ||
                it == component.flattenToShortString()
        } == true
}
