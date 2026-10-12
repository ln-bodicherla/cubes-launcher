package com.cubeslauncher.weather

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cubeslauncher.weather.model.SavedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_prefs")

class WeatherPreferencesManager(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val TEMPERATURE_UNIT_KEY = stringPreferencesKey("temperature_unit")
        private val WIND_SPEED_UNIT_KEY = stringPreferencesKey("wind_speed_unit")
        private val SAVED_LOCATIONS_KEY = stringPreferencesKey("saved_locations")
        private val LAST_WEATHER_CACHE_KEY = stringPreferencesKey("last_weather_cache")
        private val SELECTED_LOCATION_INDEX_KEY = stringPreferencesKey("selected_location_index")
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

    // --- Temperature unit ---
    val temperatureUnit: Flow<String> = context.dataStore.data.map { it[TEMPERATURE_UNIT_KEY] ?: "fahrenheit" }

    suspend fun setTemperatureUnit(unit: String) {
        context.dataStore.edit { it[TEMPERATURE_UNIT_KEY] = unit }
    }

    // --- Wind speed unit ---
    val windSpeedUnit: Flow<String> = context.dataStore.data.map { it[WIND_SPEED_UNIT_KEY] ?: "mph" }

    suspend fun setWindSpeedUnit(unit: String) {
        context.dataStore.edit { it[WIND_SPEED_UNIT_KEY] = unit }
    }

    // --- Selected location index ---
    val selectedLocationIndex: Flow<Int> = context.dataStore.data.map {
        (it[SELECTED_LOCATION_INDEX_KEY] ?: "0").toIntOrNull() ?: 0
    }

    suspend fun setSelectedLocationIndex(index: Int) {
        context.dataStore.edit { it[SELECTED_LOCATION_INDEX_KEY] = index.toString() }
    }

    // --- Saved locations ---
    val savedLocations: Flow<List<SavedLocation>> = context.dataStore.data.map { prefs ->
        val json = prefs[SAVED_LOCATIONS_KEY] ?: "[]"
        parseLocationsJson(json)
    }

    suspend fun setSavedLocations(locations: List<SavedLocation>) {
        val json = locationsToJson(locations)
        context.dataStore.edit { it[SAVED_LOCATIONS_KEY] = json }
    }

    suspend fun addLocation(location: SavedLocation) {
        context.dataStore.edit { prefs ->
            val existing = parseLocationsJson(prefs[SAVED_LOCATIONS_KEY] ?: "[]")
            val updated = existing.toMutableList()
            // Avoid duplicates by lat/lon
            if (updated.none { it.latitude == location.latitude && it.longitude == location.longitude }) {
                updated.add(location)
            }
            prefs[SAVED_LOCATIONS_KEY] = locationsToJson(updated)
        }
    }

    suspend fun removeLocation(index: Int) {
        context.dataStore.edit { prefs ->
            val existing = parseLocationsJson(prefs[SAVED_LOCATIONS_KEY] ?: "[]")
            val updated = existing.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
            }
            prefs[SAVED_LOCATIONS_KEY] = locationsToJson(updated)
        }
    }

    // --- Weather cache ---
    val lastWeatherCache: Flow<String> = context.dataStore.data.map { it[LAST_WEATHER_CACHE_KEY] ?: "" }

    suspend fun setLastWeatherCache(json: String) {
        context.dataStore.edit { it[LAST_WEATHER_CACHE_KEY] = json }
    }

    // --- JSON helpers ---
    private fun parseLocationsJson(json: String): List<SavedLocation> {
        return try {
            val array = JSONArray(json)
            val locations = mutableListOf<SavedLocation>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                locations.add(
                    SavedLocation(
                        name = obj.getString("name"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        country = obj.optString("country", ""),
                        admin = obj.optString("admin", "")
                    )
                )
            }
            locations
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun locationsToJson(locations: List<SavedLocation>): String {
        val array = JSONArray()
        for (loc in locations) {
            val obj = JSONObject()
            obj.put("name", loc.name)
            obj.put("latitude", loc.latitude)
            obj.put("longitude", loc.longitude)
            obj.put("country", loc.country)
            obj.put("admin", loc.admin)
            array.put(obj)
        }
        return array.toString()
    }
}
