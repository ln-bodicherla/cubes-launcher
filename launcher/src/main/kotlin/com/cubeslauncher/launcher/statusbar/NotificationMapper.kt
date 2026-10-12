package com.cubeslauncher.launcher.statusbar

import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import com.cubeslauncher.launcher.model.NotificationInfo

object NotificationMapper {

    fun map(context: Context, sbn: StatusBarNotification): NotificationInfo? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            sbn.packageName
        }

        val appIcon = try {
            pm.getApplicationIcon(sbn.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val body = extras.getCharSequence("android.text")?.toString() ?: ""

        // Skip empty notifications
        if (title.isEmpty() && body.isEmpty()) return null

        return NotificationInfo(
            key = sbn.key,
            appName = appName,
            appIcon = appIcon,
            smallIcon = notification.smallIcon,
            title = title,
            body = body,
            timestamp = sbn.postTime,
            packageName = sbn.packageName
        )
    }
}
