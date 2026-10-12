package com.cubeslauncher.clock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.cubeslauncher.clock.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private var wakeLock: PowerManager.WakeLock? = null

        fun acquireWakeLock(context: Context) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "cubeslauncher:alarm_wakelock"
            ).apply {
                acquire(60_000L) // 60 second timeout
            }
        }

        fun releaseWakeLock() {
            try {
                wakeLock?.let {
                    if (it.isHeld) it.release()
                }
            } catch (_: Exception) { }
            wakeLock = null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarm_id", -1)
        val hour = intent.getIntExtra("alarm_hour", 0)
        val minute = intent.getIntExtra("alarm_minute", 0)
        val label = intent.getStringExtra("alarm_label") ?: ""
        val repeatDays = intent.getIntArrayExtra("alarm_repeat_days")

        // Acquire wake lock to keep device awake until service starts
        acquireWakeLock(context)

        // Start the foreground alarm service
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("alarm_hour", hour)
            putExtra("alarm_minute", minute)
            putExtra("alarm_label", label)
            if (repeatDays != null) {
                putExtra("alarm_repeat_days", repeatDays)
            }
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
