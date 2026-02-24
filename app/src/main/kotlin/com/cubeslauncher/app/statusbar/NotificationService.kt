package com.cubeslauncher.app.statusbar

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cubeslauncher.app.model.NotificationInfo

class NotificationService : NotificationListenerService() {

    companion object {
        var instance: NotificationService? = null
            private set

        var onNotificationsChanged: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        onNotificationsChanged?.invoke()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        onNotificationsChanged?.invoke()
    }

    override fun onListenerConnected() {
        onNotificationsChanged?.invoke()
    }

    fun getActiveNotificationInfos(): List<NotificationInfo> {
        return try {
            activeNotifications
                ?.sortedByDescending { it.postTime }
                ?.mapNotNull { NotificationMapper.map(this, it) }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun dismissNotification(key: String) {
        cancelNotification(key)
    }

    fun dismissAll() {
        cancelAllNotifications()
    }
}
