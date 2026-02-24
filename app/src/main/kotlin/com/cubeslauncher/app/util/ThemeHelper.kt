package com.cubeslauncher.app.util

import android.app.Activity
import com.cubeslauncher.app.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object ThemeHelper {

    fun applyTheme(activity: Activity) {
        val prefs = PreferencesManager(activity.applicationContext)
        val mode = runBlocking { prefs.themeMode.first() }
        val themeRes = when (mode) {
            "light" -> R.style.Theme_CubesLauncher_Light
            else -> R.style.Theme_CubesLauncher_Dark
        }
        activity.setTheme(themeRes)
    }
}
