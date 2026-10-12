package com.cubeslauncher.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cubeslauncher.common.intents.CubesIntents
import com.cubeslauncher.common.prefs.CommonPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ThemeChangedReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CubesIntents.ACTION_THEME_CHANGED) return

        val themeMode = intent.getStringExtra(CubesIntents.EXTRA_THEME_MODE) ?: return
        val accentColor = intent.getStringExtra(CubesIntents.EXTRA_ACCENT_COLOR) ?: return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val commonPrefs = CommonPreferencesManager(context.applicationContext)
                commonPrefs.setThemeMode(themeMode)
                commonPrefs.setAccentColor(accentColor)

                val localPrefs = KeyboardPreferencesManager(context.applicationContext)
                localPrefs.setThemeMode(themeMode)
                localPrefs.setAccentColor(accentColor)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
