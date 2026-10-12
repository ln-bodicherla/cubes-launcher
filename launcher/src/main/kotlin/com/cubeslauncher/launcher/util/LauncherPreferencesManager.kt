package com.cubeslauncher.launcher.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cubeslauncher.common.intents.CubesIntents
import com.cubeslauncher.launcher.model.TileSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cubes_prefs")

class LauncherPreferencesManager(private val context: Context) {

    companion object {
        private val PINNED_APPS_KEY = stringSetPreferencesKey("pinned_apps")
        private val BG_MODE_KEY = stringPreferencesKey("bg_mode")
        private val BG_IMAGE_URI_KEY = stringPreferencesKey("bg_image_uri")
        private val TILE_TRANSPARENCY_KEY = intPreferencesKey("tile_transparency")
        private val SHOW_MORE_TILES_KEY = booleanPreferencesKey("show_more_tiles")
        private val TILE_MODE_KEY = booleanPreferencesKey("tile_mode")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val LOCK_BG_URI_KEY = stringPreferencesKey("lock_bg_uri")
        private val TILE_SIZES_KEY = stringPreferencesKey("tile_sizes")
        private val PINNED_APPS_ORDER_KEY = stringPreferencesKey("pinned_apps_order")
        private val TILE_MODE_ORDER_KEY = stringPreferencesKey("tile_mode_order")
        private val MINIMAL_TILES_KEY = booleanPreferencesKey("minimal_tiles")
        private val USAGE_TRACKING_KEY = booleanPreferencesKey("usage_tracking")
        private val APP_LAUNCH_COUNTS_KEY = stringPreferencesKey("app_launch_counts")
        private val TILE_SIZES_TILE_MODE_KEY = stringPreferencesKey("tile_sizes_tile_mode")
        private val TILE_SIZES_MINIMAL_TILES_KEY = stringPreferencesKey("tile_sizes_minimal_tiles")
        private val TILE_SIZES_USAGE_TRACKING_KEY = stringPreferencesKey("tile_sizes_usage_tracking")
        private val TILE_SIZES_DEFAULT_KEY = stringPreferencesKey("tile_sizes_default")
        private val LEGACY_MIGRATED_KEY = booleanPreferencesKey("legacy_packages_migrated")
    }

    val pinnedApps: Flow<List<String>> = context.dataStore.data.map { prefs: Preferences ->
        val set: Set<String>? = prefs[PINNED_APPS_KEY]
        set?.toList() ?: emptyList()
    }

    val pinnedAppsOrdered: Flow<List<String>> = context.dataStore.data.map { prefs: Preferences ->
        val json = prefs[PINNED_APPS_ORDER_KEY]
        if (json != null) {
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) {
                prefs[PINNED_APPS_KEY]?.toList() ?: emptyList()
            }
        } else {
            prefs[PINNED_APPS_KEY]?.toList() ?: emptyList()
        }
    }

    val tileModeOrder: Flow<List<String>> = context.dataStore.data.map { prefs: Preferences ->
        val json = prefs[TILE_MODE_ORDER_KEY]
        if (json != null) {
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun savePinnedAppsOrder(order: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[PINNED_APPS_ORDER_KEY] = JSONArray(order).toString()
        }
    }

    suspend fun saveTileModeOrder(order: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[TILE_MODE_ORDER_KEY] = JSONArray(order).toString()
        }
    }

    suspend fun pinApp(packageName: String): Boolean {
        var added = false
        context.dataStore.edit { prefs: MutablePreferences ->
            val set: Set<String> = prefs[PINNED_APPS_KEY] ?: emptySet()
            val current: MutableSet<String> = set.toMutableSet()
            if (current.add(packageName)) {
                prefs[PINNED_APPS_KEY] = current
                added = true
                val json = prefs[PINNED_APPS_ORDER_KEY]
                val arr = if (json != null) try { JSONArray(json) } catch (_: Exception) { JSONArray() } else JSONArray()
                arr.put(packageName)
                prefs[PINNED_APPS_ORDER_KEY] = arr.toString()
            }
        }
        return added
    }

    suspend fun unpinApp(packageName: String) {
        context.dataStore.edit { prefs: MutablePreferences ->
            val set: Set<String> = prefs[PINNED_APPS_KEY] ?: emptySet()
            val current: MutableSet<String> = set.toMutableSet()
            current.remove(packageName)
            prefs[PINNED_APPS_KEY] = current
            val json = prefs[PINNED_APPS_ORDER_KEY]
            if (json != null) {
                try {
                    val arr = JSONArray(json)
                    val newArr = JSONArray()
                    for (i in 0 until arr.length()) {
                        if (arr.getString(i) != packageName) newArr.put(arr.getString(i))
                    }
                    prefs[PINNED_APPS_ORDER_KEY] = newArr.toString()
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun isPinned(packageName: String): Boolean {
        val prefs: Preferences = context.dataStore.data.first()
        return prefs[PINNED_APPS_KEY]?.contains(packageName) == true
    }

    // --- Background mode ---
    val bgMode: Flow<String> = context.dataStore.data.map { it[BG_MODE_KEY] ?: "none" }

    suspend fun setBgMode(mode: String) {
        context.dataStore.edit { it[BG_MODE_KEY] = mode }
    }

    // --- Background image URI ---
    val bgImageUri: Flow<String?> = context.dataStore.data.map { it[BG_IMAGE_URI_KEY] }

    suspend fun setBgImageUri(uri: String?) {
        context.dataStore.edit {
            if (uri != null) it[BG_IMAGE_URI_KEY] = uri else it.remove(BG_IMAGE_URI_KEY)
        }
    }

    // --- Tile transparency (0–100) ---
    val tileTransparency: Flow<Int> = context.dataStore.data.map { it[TILE_TRANSPARENCY_KEY] ?: 0 }

    suspend fun setTileTransparency(value: Int) {
        context.dataStore.edit { it[TILE_TRANSPARENCY_KEY] = value.coerceIn(0, 100) }
    }

    // --- Show more tiles (6 cols vs 4) ---
    val showMoreTiles: Flow<Boolean> = context.dataStore.data.map { it[SHOW_MORE_TILES_KEY] ?: false }

    suspend fun setShowMoreTiles(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_MORE_TILES_KEY] = enabled }
    }

    // --- Tile mode ---
    val tileMode: Flow<Boolean> = context.dataStore.data.map { it[TILE_MODE_KEY] ?: false }

    suspend fun setTileMode(enabled: Boolean) {
        context.dataStore.edit { it[TILE_MODE_KEY] = enabled }
    }

    // --- Minimal tiles ---
    val minimalTiles: Flow<Boolean> = context.dataStore.data.map { it[MINIMAL_TILES_KEY] ?: false }

    suspend fun setMinimalTiles(enabled: Boolean) {
        context.dataStore.edit { it[MINIMAL_TILES_KEY] = enabled }
    }

    // --- Theme mode ---
    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE_KEY] ?: "dark" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    // --- Accent color ---
    val accentColor: Flow<String> = context.dataStore.data.map { it[ACCENT_COLOR_KEY] ?: "#0078D7" }

    suspend fun setAccentColor(hex: String) {
        context.dataStore.edit { it[ACCENT_COLOR_KEY] = hex }
    }

    // --- Lock screen background URI ---
    val lockBgUri: Flow<String?> = context.dataStore.data.map { it[LOCK_BG_URI_KEY] }

    suspend fun setLockBgUri(uri: String?) {
        context.dataStore.edit {
            if (uri != null) it[LOCK_BG_URI_KEY] = uri else it.remove(LOCK_BG_URI_KEY)
        }
    }

    // --- Tile sizes per app ---
    val tileSizes: Flow<Map<String, TileSize>> = context.dataStore.data.map { prefs ->
        val json = prefs[TILE_SIZES_KEY] ?: return@map emptyMap()
        try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, TileSize>()
            for (key in obj.keys()) {
                map[key] = try {
                    TileSize.valueOf(obj.getString(key))
                } catch (_: Exception) {
                    TileSize.SMALL
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun setTileSize(packageName: String, size: TileSize) {
        context.dataStore.edit { prefs ->
            val json = prefs[TILE_SIZES_KEY] ?: "{}"
            val obj = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
            if (size == TileSize.SMALL) {
                obj.remove(packageName)
            } else {
                obj.put(packageName, size.name)
            }
            prefs[TILE_SIZES_KEY] = obj.toString()
        }
    }

    // --- Usage tracking ---
    val usageTracking: Flow<Boolean> = context.dataStore.data.map { it[USAGE_TRACKING_KEY] ?: false }

    suspend fun setUsageTracking(enabled: Boolean) {
        context.dataStore.edit { it[USAGE_TRACKING_KEY] = enabled }
    }

    val appLaunchCounts: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        val json = prefs[APP_LAUNCH_COUNTS_KEY] ?: return@map emptyMap()
        try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            for (key in obj.keys()) {
                map[key] = obj.getInt(key)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun recordAppLaunch(packageName: String) {
        context.dataStore.edit { prefs ->
            if (prefs[USAGE_TRACKING_KEY] != true) return@edit

            val countsJson = prefs[APP_LAUNCH_COUNTS_KEY] ?: "{}"
            val countsObj = try { JSONObject(countsJson) } catch (_: Exception) { JSONObject() }
            val newCount = countsObj.optInt(packageName, 0) + 1
            countsObj.put(packageName, newCount)
            prefs[APP_LAUNCH_COUNTS_KEY] = countsObj.toString()

            val targetSize = when {
                newCount >= 25 -> TileSize.WIDE
                newCount >= 10 -> TileSize.MEDIUM
                else -> TileSize.SMALL
            }
            val sizesJson = prefs[TILE_SIZES_KEY] ?: "{}"
            val sizesObj = try { JSONObject(sizesJson) } catch (_: Exception) { JSONObject() }
            if (targetSize == TileSize.SMALL) {
                sizesObj.remove(packageName)
            } else {
                sizesObj.put(packageName, targetSize.name)
            }
            prefs[TILE_SIZES_KEY] = sizesObj.toString()
        }
    }

    // --- Per-mode tile size memory ---

    private fun tileSizesKeyForMode(mode: String): Preferences.Key<String> = when (mode) {
        "tile_mode" -> TILE_SIZES_TILE_MODE_KEY
        "minimal_tiles" -> TILE_SIZES_MINIMAL_TILES_KEY
        "usage_tracking" -> TILE_SIZES_USAGE_TRACKING_KEY
        else -> TILE_SIZES_DEFAULT_KEY
    }

    suspend fun getActiveTileMode(): String {
        val prefs = context.dataStore.data.first()
        return when {
            prefs[TILE_MODE_KEY] == true -> "tile_mode"
            prefs[MINIMAL_TILES_KEY] == true -> "minimal_tiles"
            prefs[USAGE_TRACKING_KEY] == true -> "usage_tracking"
            else -> "default"
        }
    }

    suspend fun saveTileSizesForMode(mode: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[TILE_SIZES_KEY] ?: "{}"
            prefs[tileSizesKeyForMode(mode)] = current
        }
    }

    suspend fun loadTileSizesForMode(mode: String) {
        context.dataStore.edit { prefs ->
            val saved = prefs[tileSizesKeyForMode(mode)] ?: "{}"
            prefs[TILE_SIZES_KEY] = saved
        }
    }

    /**
     * Migrates legacy pseudo-package names to real app IDs.
     * Call once on first launch after the split.
     */
    suspend fun migrateLegacyPackageNames() {
        context.dataStore.edit { prefs ->
            if (prefs[LEGACY_MIGRATED_KEY] == true) return@edit

            val legacyMap = mapOf(
                CubesIntents.LEGACY_PHONE_PACKAGE to CubesIntents.PHONE_APP_ID,
                CubesIntents.LEGACY_CONTACTS_PACKAGE to CubesIntents.CONTACTS_APP_ID,
                CubesIntents.LEGACY_MESSAGES_PACKAGE to CubesIntents.MESSAGES_APP_ID
            )

            // Migrate pinned_apps set
            val pinned = prefs[PINNED_APPS_KEY]
            if (pinned != null) {
                prefs[PINNED_APPS_KEY] = pinned.map { legacyMap[it] ?: it }.toSet()
            }

            // Migrate pinned_apps_order JSON array
            val orderJson = prefs[PINNED_APPS_ORDER_KEY]
            if (orderJson != null) {
                try {
                    val arr = JSONArray(orderJson)
                    val newArr = JSONArray()
                    for (i in 0 until arr.length()) {
                        val pkg = arr.getString(i)
                        newArr.put(legacyMap[pkg] ?: pkg)
                    }
                    prefs[PINNED_APPS_ORDER_KEY] = newArr.toString()
                } catch (_: Exception) { }
            }

            // Migrate tile_sizes JSON object keys
            for (key in listOf(TILE_SIZES_KEY, TILE_SIZES_TILE_MODE_KEY, TILE_SIZES_MINIMAL_TILES_KEY,
                    TILE_SIZES_USAGE_TRACKING_KEY, TILE_SIZES_DEFAULT_KEY)) {
                val json = prefs[key]
                if (json != null) {
                    try {
                        val obj = JSONObject(json)
                        val newObj = JSONObject()
                        for (k in obj.keys()) {
                            newObj.put(legacyMap[k] ?: k, obj.get(k))
                        }
                        prefs[key] = newObj.toString()
                    } catch (_: Exception) { }
                }
            }

            prefs[LEGACY_MIGRATED_KEY] = true
        }
    }
}
