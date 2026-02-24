package com.cubeslauncher.app.util

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
import com.cubeslauncher.app.messages.model.ScheduledMessage
import com.cubeslauncher.app.model.TileSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cubes_prefs")

class PreferencesManager(private val context: Context) {

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
        private val ARCHIVED_THREADS_KEY = stringSetPreferencesKey("archived_threads")
        private val DELETED_THREADS_KEY = stringSetPreferencesKey("deleted_threads")
        private val SCHEDULED_MESSAGES_KEY = stringPreferencesKey("scheduled_messages")

        // Phone settings
        private val PHONE_CALLER_ID_KEY = stringPreferencesKey("phone_caller_id")
        private val PHONE_CALL_WAITING_KEY = booleanPreferencesKey("phone_call_waiting")
        private val PHONE_VOICEMAIL_NUMBER_KEY = stringPreferencesKey("phone_voicemail_number")
        private val PHONE_INTERNATIONAL_ASSIST_KEY = booleanPreferencesKey("phone_international_assist")

        // People settings
        private val CONTACTS_SORT_ORDER_KEY = stringPreferencesKey("contacts_sort_order")
        private val CONTACTS_DISPLAY_FORMAT_KEY = stringPreferencesKey("contacts_display_format")
        private val CONTACTS_HIDE_NO_PHONE_KEY = booleanPreferencesKey("contacts_hide_no_phone")

        // Messaging settings
        private val MESSAGES_SMS_DELIVERY_KEY = booleanPreferencesKey("messages_sms_delivery")
        private val MESSAGES_MMS_DELIVERY_KEY = booleanPreferencesKey("messages_mms_delivery")
        private val MESSAGES_MMS_AUTO_DOWNLOAD_KEY = booleanPreferencesKey("messages_mms_auto_download")
        private val MESSAGES_GROUP_MESSAGING_KEY = booleanPreferencesKey("messages_group_messaging")

        const val MAX_PINNED_APPS = 8
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
            if (current.size < MAX_PINNED_APPS && current.add(packageName)) {
                prefs[PINNED_APPS_KEY] = current
                added = true
                // Also append to ordered list
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
            // Also remove from ordered list
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

    // --- Tile mode (show all apps as tiles) ---
    val tileMode: Flow<Boolean> = context.dataStore.data.map { it[TILE_MODE_KEY] ?: false }

    suspend fun setTileMode(enabled: Boolean) {
        context.dataStore.edit { it[TILE_MODE_KEY] = enabled }
    }

    // --- Minimal tiles (pinned + notification apps only) ---
    val minimalTiles: Flow<Boolean> = context.dataStore.data.map { it[MINIMAL_TILES_KEY] ?: false }

    suspend fun setMinimalTiles(enabled: Boolean) {
        context.dataStore.edit { it[MINIMAL_TILES_KEY] = enabled }
    }

    // --- Theme mode (dark / light) ---
    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE_KEY] ?: "dark" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    // --- Accent color (hex string like "#0078D7") ---
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

    // --- Tile sizes per app (JSON map) ---
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

    /**
     * Increments the launch count for a package. If usage tracking is enabled,
     * also auto-resizes the tile based on thresholds:
     *   0–9  → SMALL
     *   10–24 → MEDIUM
     *   25+   → WIDE
     */
    suspend fun recordAppLaunch(packageName: String) {
        context.dataStore.edit { prefs ->
            if (prefs[USAGE_TRACKING_KEY] != true) return@edit

            // Increment count
            val countsJson = prefs[APP_LAUNCH_COUNTS_KEY] ?: "{}"
            val countsObj = try { JSONObject(countsJson) } catch (_: Exception) { JSONObject() }
            val newCount = countsObj.optInt(packageName, 0) + 1
            countsObj.put(packageName, newCount)
            prefs[APP_LAUNCH_COUNTS_KEY] = countsObj.toString()

            // Auto-resize tile
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

    // --- Archived threads ---

    val archivedThreads: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        prefs[ARCHIVED_THREADS_KEY]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    suspend fun archiveThread(threadId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[ARCHIVED_THREADS_KEY] ?: emptySet()
            prefs[ARCHIVED_THREADS_KEY] = current + threadId.toString()
        }
    }

    suspend fun unarchiveThread(threadId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[ARCHIVED_THREADS_KEY] ?: emptySet()
            prefs[ARCHIVED_THREADS_KEY] = current - threadId.toString()
        }
    }

    // --- Deleted threads (soft delete / trash) ---

    val deletedThreads: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        prefs[DELETED_THREADS_KEY]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    suspend fun softDeleteThread(threadId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[DELETED_THREADS_KEY] ?: emptySet()
            prefs[DELETED_THREADS_KEY] = current + threadId.toString()
        }
    }

    suspend fun restoreDeletedThread(threadId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[DELETED_THREADS_KEY] ?: emptySet()
            prefs[DELETED_THREADS_KEY] = current - threadId.toString()
        }
    }

    // --- Scheduled messages ---

    val scheduledMessages: Flow<List<ScheduledMessage>> = context.dataStore.data.map { prefs ->
        val json = prefs[SCHEDULED_MESSAGES_KEY] ?: return@map emptyList()
        try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ScheduledMessage(
                    id = obj.getString("id"),
                    address = obj.getString("address"),
                    body = obj.getString("body"),
                    scheduledTimeMillis = obj.getLong("scheduledTimeMillis"),
                    createdAt = obj.getLong("createdAt")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveScheduledMessage(msg: ScheduledMessage) {
        context.dataStore.edit { prefs ->
            val json = prefs[SCHEDULED_MESSAGES_KEY] ?: "[]"
            val arr = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("address", msg.address)
                put("body", msg.body)
                put("scheduledTimeMillis", msg.scheduledTimeMillis)
                put("createdAt", msg.createdAt)
            }
            arr.put(obj)
            prefs[SCHEDULED_MESSAGES_KEY] = arr.toString()
        }
    }

    suspend fun removeScheduledMessage(id: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[SCHEDULED_MESSAGES_KEY] ?: return@edit
            try {
                val arr = JSONArray(json)
                val newArr = JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getString("id") != id) {
                        newArr.put(obj)
                    }
                }
                prefs[SCHEDULED_MESSAGES_KEY] = newArr.toString()
            } catch (_: Exception) { }
        }
    }

    // --- Phone settings ---

    val phoneCallerId: Flow<String> = context.dataStore.data.map { it[PHONE_CALLER_ID_KEY] ?: "network" }

    suspend fun setPhoneCallerId(mode: String) {
        context.dataStore.edit { it[PHONE_CALLER_ID_KEY] = mode }
    }

    val phoneCallWaiting: Flow<Boolean> = context.dataStore.data.map { it[PHONE_CALL_WAITING_KEY] ?: true }

    suspend fun setPhoneCallWaiting(enabled: Boolean) {
        context.dataStore.edit { it[PHONE_CALL_WAITING_KEY] = enabled }
    }

    val phoneVoicemailNumber: Flow<String> = context.dataStore.data.map { it[PHONE_VOICEMAIL_NUMBER_KEY] ?: "" }

    suspend fun setPhoneVoicemailNumber(number: String) {
        context.dataStore.edit { it[PHONE_VOICEMAIL_NUMBER_KEY] = number }
    }

    val phoneInternationalAssist: Flow<Boolean> = context.dataStore.data.map { it[PHONE_INTERNATIONAL_ASSIST_KEY] ?: true }

    suspend fun setPhoneInternationalAssist(enabled: Boolean) {
        context.dataStore.edit { it[PHONE_INTERNATIONAL_ASSIST_KEY] = enabled }
    }

    // --- People settings ---

    val contactsSortOrder: Flow<String> = context.dataStore.data.map { it[CONTACTS_SORT_ORDER_KEY] ?: "first_name" }

    suspend fun setContactsSortOrder(order: String) {
        context.dataStore.edit { it[CONTACTS_SORT_ORDER_KEY] = order }
    }

    val contactsDisplayFormat: Flow<String> = context.dataStore.data.map { it[CONTACTS_DISPLAY_FORMAT_KEY] ?: "first_last" }

    suspend fun setContactsDisplayFormat(format: String) {
        context.dataStore.edit { it[CONTACTS_DISPLAY_FORMAT_KEY] = format }
    }

    val contactsHideNoPhone: Flow<Boolean> = context.dataStore.data.map { it[CONTACTS_HIDE_NO_PHONE_KEY] ?: false }

    suspend fun setContactsHideNoPhone(enabled: Boolean) {
        context.dataStore.edit { it[CONTACTS_HIDE_NO_PHONE_KEY] = enabled }
    }

    // --- Messaging settings ---

    val messagesSmsDelivery: Flow<Boolean> = context.dataStore.data.map { it[MESSAGES_SMS_DELIVERY_KEY] ?: false }

    suspend fun setMessagesSmsDelivery(enabled: Boolean) {
        context.dataStore.edit { it[MESSAGES_SMS_DELIVERY_KEY] = enabled }
    }

    val messagesMmsDelivery: Flow<Boolean> = context.dataStore.data.map { it[MESSAGES_MMS_DELIVERY_KEY] ?: false }

    suspend fun setMessagesMmsDelivery(enabled: Boolean) {
        context.dataStore.edit { it[MESSAGES_MMS_DELIVERY_KEY] = enabled }
    }

    val messagesMmsAutoDownload: Flow<Boolean> = context.dataStore.data.map { it[MESSAGES_MMS_AUTO_DOWNLOAD_KEY] ?: true }

    suspend fun setMessagesMmsAutoDownload(enabled: Boolean) {
        context.dataStore.edit { it[MESSAGES_MMS_AUTO_DOWNLOAD_KEY] = enabled }
    }

    val messagesGroupMessaging: Flow<Boolean> = context.dataStore.data.map { it[MESSAGES_GROUP_MESSAGING_KEY] ?: true }

    suspend fun setMessagesGroupMessaging(enabled: Boolean) {
        context.dataStore.edit { it[MESSAGES_GROUP_MESSAGING_KEY] = enabled }
    }
}
