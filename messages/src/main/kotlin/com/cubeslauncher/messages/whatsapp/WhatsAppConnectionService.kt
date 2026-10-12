package com.cubeslauncher.messages.whatsapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.cubeslauncher.messages.R

class WhatsAppConnectionService : Service() {

    companion object {
        private const val CHANNEL_ID = "whatsapp_connection"
        private const val NOTIFICATION_ID = 9001

        fun start(context: Context) {
            val intent = Intent(context, WhatsAppConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WhatsAppConnectionService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        WhatsAppBridge.init(applicationContext)
        WhatsAppBridge.connect()

        WhatsAppBridge.setOnConnectedListener {
            // Send "available" presence on connect
            WhatsAppBridge.sendPresence("available")
        }

        WhatsAppBridge.setOnDisconnectedListener {
            // Attempt reconnection
            WhatsAppBridge.connect()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        WhatsAppBridge.sendPresence("unavailable")
        WhatsAppBridge.setOnConnectedListener(null)
        WhatsAppBridge.setOnDisconnectedListener(null)
        WhatsAppBridge.disconnect()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.whatsapp_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.whatsapp_connected)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.whatsapp_connected))
                .setSmallIcon(R.drawable.ic_messages_send)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.whatsapp_connected))
                .setSmallIcon(R.drawable.ic_messages_send)
                .setOngoing(true)
                .build()
        }
    }
}
