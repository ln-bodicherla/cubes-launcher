package com.cubeslauncher.clock.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cubeslauncher.clock.ClockPreferencesManager
import com.cubeslauncher.clock.alarm.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val prefs = ClockPreferencesManager(context.applicationContext)
                val alarms = prefs.getAlarms()
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                for (alarm in alarms) {
                    if (alarm.enabled) {
                        val triggerTime = AlarmScheduler.getNextTriggerTime(alarm)
                        val pi = AlarmScheduler.getPendingIntent(context, alarm)
                        alarmManager.setAlarmClock(
                            AlarmManager.AlarmClockInfo(triggerTime, pi),
                            pi
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
