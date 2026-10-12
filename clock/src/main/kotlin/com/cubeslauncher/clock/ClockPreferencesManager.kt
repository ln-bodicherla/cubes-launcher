package com.cubeslauncher.clock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cubeslauncher.clock.model.Alarm
import com.cubeslauncher.clock.model.WorldClockCity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clock_prefs")

class ClockPreferencesManager(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val ALARMS_KEY = stringPreferencesKey("alarms")
        private val WORLD_CLOCK_CITIES_KEY = stringPreferencesKey("world_clock_cities")
        private val SNOOZE_DURATION_KEY = intPreferencesKey("snooze_duration")
        private val VIBRATE_ON_ALARM_KEY = booleanPreferencesKey("vibrate_on_alarm")
    }

    // --- Theme ---
    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE_KEY] ?: "dark" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    val accentColor: Flow<String> = context.dataStore.data.map { it[ACCENT_COLOR_KEY] ?: "#0078D7" }

    suspend fun setAccentColor(hex: String) {
        context.dataStore.edit { it[ACCENT_COLOR_KEY] = hex }
    }

    // --- Snooze Duration ---
    val snoozeDuration: Flow<Int> = context.dataStore.data.map { it[SNOOZE_DURATION_KEY] ?: 10 }

    suspend fun setSnoozeDuration(minutes: Int) {
        context.dataStore.edit { it[SNOOZE_DURATION_KEY] = minutes }
    }

    // --- Vibrate ---
    val vibrateOnAlarm: Flow<Boolean> = context.dataStore.data.map { it[VIBRATE_ON_ALARM_KEY] ?: true }

    suspend fun setVibrateOnAlarm(enabled: Boolean) {
        context.dataStore.edit { it[VIBRATE_ON_ALARM_KEY] = enabled }
    }

    // --- Alarms ---
    suspend fun getAlarms(): List<Alarm> {
        val json = context.dataStore.data.first()[ALARMS_KEY] ?: return emptyList()
        return parseAlarms(json)
    }

    fun getAlarmsFlow(): Flow<List<Alarm>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[ALARMS_KEY] ?: return@map emptyList()
            parseAlarms(json)
        }
    }

    suspend fun setAlarms(alarms: List<Alarm>) {
        val json = serializeAlarms(alarms)
        context.dataStore.edit { it[ALARMS_KEY] = json }
    }

    // --- World Clock Cities ---
    suspend fun getCities(): List<WorldClockCity> {
        val json = context.dataStore.data.first()[WORLD_CLOCK_CITIES_KEY] ?: return emptyList()
        return parseCities(json)
    }

    fun getCitiesFlow(): Flow<List<WorldClockCity>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[WORLD_CLOCK_CITIES_KEY] ?: return@map emptyList()
            parseCities(json)
        }
    }

    suspend fun setCities(cities: List<WorldClockCity>) {
        val json = serializeCities(cities)
        context.dataStore.edit { it[WORLD_CLOCK_CITIES_KEY] = json }
    }

    // --- JSON serialization ---
    private fun serializeAlarms(alarms: List<Alarm>): String {
        val arr = JSONArray()
        for (alarm in alarms) {
            val obj = JSONObject()
            obj.put("id", alarm.id)
            obj.put("hour", alarm.hour)
            obj.put("minute", alarm.minute)
            obj.put("enabled", alarm.enabled)
            obj.put("label", alarm.label)
            val daysArr = JSONArray()
            for (day in alarm.repeatDays) {
                daysArr.put(day)
            }
            obj.put("repeatDays", daysArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseAlarms(json: String): List<Alarm> {
        val alarms = mutableListOf<Alarm>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val daysArr = obj.optJSONArray("repeatDays")
                val days = mutableSetOf<Int>()
                if (daysArr != null) {
                    for (j in 0 until daysArr.length()) {
                        days.add(daysArr.getInt(j))
                    }
                }
                alarms.add(
                    Alarm(
                        id = obj.getInt("id"),
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        enabled = obj.optBoolean("enabled", true),
                        label = obj.optString("label", ""),
                        repeatDays = days
                    )
                )
            }
        } catch (_: Exception) { }
        return alarms
    }

    private fun serializeCities(cities: List<WorldClockCity>): String {
        val arr = JSONArray()
        for (city in cities) {
            val obj = JSONObject()
            obj.put("cityName", city.cityName)
            obj.put("timeZoneId", city.timeZoneId)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseCities(json: String): List<WorldClockCity> {
        val cities = mutableListOf<WorldClockCity>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                cities.add(
                    WorldClockCity(
                        cityName = obj.getString("cityName"),
                        timeZoneId = obj.getString("timeZoneId")
                    )
                )
            }
        } catch (_: Exception) { }
        return cities
    }
}
