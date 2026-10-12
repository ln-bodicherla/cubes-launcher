package com.cubeslauncher.clock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.cubeslauncher.clock.ClockActivity
import com.cubeslauncher.clock.R
import java.util.Locale

class StopwatchService : Service() {

    companion object {
        const val CHANNEL_ID = "stopwatch_channel"
        const val NOTIFICATION_ID = 1003

        const val ACTION_START = "start"
        const val ACTION_RESUME = "resume"
        const val ACTION_PAUSE = "pause"
        const val ACTION_STOP = "stop"
        const val EXTRA_ELAPSED_BEFORE_PAUSE = "elapsed_before_pause"

        var isRunning = false
            private set
        var serviceStartTime = 0L
            private set
        var serviceElapsedBeforePause = 0L
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                updateNotification()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                serviceElapsedBeforePause = 0L
                serviceStartTime = SystemClock.elapsedRealtime()
                isRunning = true
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                handler.post(updateRunnable)
            }
            ACTION_RESUME -> {
                serviceElapsedBeforePause = intent?.getLongExtra(EXTRA_ELAPSED_BEFORE_PAUSE, 0L) ?: 0L
                serviceStartTime = SystemClock.elapsedRealtime()
                isRunning = true
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                handler.post(updateRunnable)
            }
            ACTION_PAUSE, ACTION_STOP -> {
                isRunning = false
                handler.removeCallbacks(updateRunnable)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.stopwatch_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.stopwatch_channel_description)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, ClockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsed = serviceElapsedBeforePause + (SystemClock.elapsedRealtime() - serviceStartTime)
        val timeText = formatTime(elapsed)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.stopwatch_notification_title))
            .setContentText(timeText)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
