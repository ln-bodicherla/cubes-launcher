package com.cubeslauncher.app.util

import android.content.Context
import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object AccentColorManager {

    private const val DEFAULT_ACCENT = "#0078D7"

    private val _accentColor = MutableStateFlow(Color.parseColor(DEFAULT_ACCENT))
    val accentColorFlow: StateFlow<Int> = _accentColor.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun init(context: Context) {
        val prefs = PreferencesManager(context.applicationContext)
        val initial = runBlocking { prefs.accentColor.first() }
        _accentColor.value = parseColorSafe(initial)

        scope.launch {
            prefs.accentColor.collect { hex ->
                _accentColor.value = parseColorSafe(hex)
            }
        }
    }

    fun getAccentColor(): Int = _accentColor.value

    private fun parseColorSafe(hex: String): Int =
        try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor(DEFAULT_ACCENT) }
}
