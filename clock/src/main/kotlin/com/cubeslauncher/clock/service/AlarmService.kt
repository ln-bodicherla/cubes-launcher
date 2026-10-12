package com.cubeslauncher.clock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cubeslauncher.clock.ClockPreferencesManager
import com.cubeslauncher.clock.R
import com.cubeslauncher.clock.alarm.AlarmRingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1001

        private var mediaPlayer: MediaPlayer? = null
        private var vibrator: Vibrator? = null

        fun stopAlarmSound() {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Exception) { }
            mediaPlayer = null

            try {
                vibrator?.cancel()
            } catch (_: Exception) { }
            vibrator = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra("alarm_id", -1) ?: -1
        val hour = intent?.getIntExtra("alarm_hour", 0) ?: 0
        val minute = intent?.getIntExtra("alarm_minute", 0) ?: 0
        val label = intent?.getStringExtra("alarm_label") ?: ""

        createNotificationChannel()

        // Create pending intent to launch AlarmRingActivity
        val ringIntent = Intent(this, AlarmRingActivity::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("alarm_hour", hour)
            putExtra("alarm_minute", minute)
            putExtra("alarm_label", label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            alarmId,
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = String.format("%02d:%02d", hour, minute)
        val contentText = if (label.isNotEmpty()) "$timeText - $label" else timeText

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.alarm_notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Release the wake lock acquired by AlarmReceiver
        com.cubeslauncher.clock.receiver.AlarmReceiver.releaseWakeLock()

        // Start alarm sound and vibration in the service so it plays
        // even if the activity doesn't launch (Android 12+ restrictions)
        startAlarmSound()
        startVibration()

        return START_NOT_STICKY
    }

    private fun startAlarmSound() {
        try {
            stopAlarmSound()

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) { }
    }

    private fun startVibration() {
        val shouldVibrate = runBlocking {
            ClockPreferencesManager(applicationContext).vibrateOnAlarm.first()
        }
        if (!shouldVibrate) return

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_channel_description)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null) // Sound is handled by MediaPlayer
            enableVibration(false) // Vibration is handled manually
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopAlarmSound()
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }
}
