package com.cubeslauncher.common.theme

import android.app.Activity
import com.cubeslauncher.common.R
import com.cubeslauncher.common.prefs.CommonPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object ThemeHelper {

    fun applyTheme(activity: Activity) {
        val prefs = CommonPreferencesManager(activity.applicationContext)
        val mode = runBlocking { prefs.themeMode.first() }
        val themeRes = when (mode) {
            "light" -> R.style.Theme_CubesLauncher_Light
            else -> R.style.Theme_CubesLauncher_Dark
        }
        activity.setTheme(themeRes)
    }
}
