package com.cubeslauncher.clock.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.cubeslauncher.clock.ClockPreferencesManager
import com.cubeslauncher.clock.databinding.ActivityAlarmRingBinding
import com.cubeslauncher.clock.service.AlarmService
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class AlarmRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        // Show on lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Keep screen on while alarm screen is showing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val hour = intent.getIntExtra("alarm_hour", 0)
        val minute = intent.getIntExtra("alarm_minute", 0)
        val label = intent.getStringExtra("alarm_label") ?: ""

        binding.timeText.text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        binding.labelText.text = label
        if (label.isEmpty()) {
            binding.labelText.visibility = android.view.View.GONE
        }

        val accent = AccentColorManager.getAccentColor()
        binding.dismissButton.setBackgroundColor(accent)
        binding.dismissButton.setTextColor(Color.WHITE)

        binding.dismissButton.setOnClickListener {
            stopAlarmService()
            finish()
        }

        binding.snoozeButton.setOnClickListener {
            snooze(hour, minute, label)
            stopAlarmService()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun stopAlarmService() {
        AlarmService.stopAlarmSound()
        val serviceIntent = Intent(this, AlarmService::class.java)
        stopService(serviceIntent)
    }

    private fun snooze(hour: Int, minute: Int, label: String) {
        lifecycleScope.launch {
            val snoozeMins = ClockPreferencesManager(applicationContext).snoozeDuration.first()
            val triggerTime = System.currentTimeMillis() + snoozeMins * 60 * 1000L

            val intent = Intent(
                this@AlarmRingActivity,
                com.cubeslauncher.clock.receiver.AlarmReceiver::class.java
            ).apply {
                putExtra("alarm_id", -999) // Snooze ID
                putExtra("alarm_hour", hour)
                putExtra("alarm_minute", minute)
                putExtra("alarm_label", label)
            }

            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this@AlarmRingActivity,
                -999,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.setAlarmClock(
                android.app.AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                pendingIntent
            )
        }
    }
}
