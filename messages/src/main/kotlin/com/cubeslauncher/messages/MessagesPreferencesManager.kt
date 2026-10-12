package com.cubeslauncher.messages

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cubeslauncher.messages.model.ScheduledMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "messages_prefs")

class MessagesPreferencesManager(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val ARCHIVED_THREADS_KEY = stringSetPreferencesKey("archived_threads")
        private val DELETED_THREADS_KEY = stringSetPreferencesKey("deleted_threads")
        private val SCHEDULED_MESSAGES_KEY = stringPreferencesKey("scheduled_messages")
        private val MESSAGES_SMS_DELIVERY_KEY = booleanPreferencesKey("messages_sms_delivery")
        private val MESSAGES_MMS_DELIVERY_KEY = booleanPreferencesKey("messages_mms_delivery")
        private val MESSAGES_MMS_AUTO_DOWNLOAD_KEY = booleanPreferencesKey("messages_mms_auto_download")
        private val MESSAGES_GROUP_MESSAGING_KEY = booleanPreferencesKey("messages_group_messaging")
        private val WHATSAPP_ENABLED_KEY = booleanPreferencesKey("whatsapp_enabled")
        private val LIST_TEXT_SCALE_KEY = floatPreferencesKey("list_text_scale")
        private val MESSAGE_TEXT_SCALE_KEY = floatPreferencesKey("message_text_scale")
        private val SWIPE_RIGHT_ACTION_KEY = stringPreferencesKey("swipe_right_action")
        private val SWIPE_LEFT_ACTION_KEY = stringPreferencesKey("swipe_left_action")
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

    // --- Deleted threads ---
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

    // --- Text scale settings ---
    val listTextScale: Flow<Float> = context.dataStore.data.map { it[LIST_TEXT_SCALE_KEY] ?: 1.0f }

    suspend fun setListTextScale(scale: Float) {
        context.dataStore.edit { it[LIST_TEXT_SCALE_KEY] = scale }
    }

    val messageTextScale: Flow<Float> = context.dataStore.data.map { it[MESSAGE_TEXT_SCALE_KEY] ?: 1.0f }

    suspend fun setMessageTextScale(scale: Float) {
        context.dataStore.edit { it[MESSAGE_TEXT_SCALE_KEY] = scale }
    }

    // --- Swipe action settings ---
    val swipeRightAction: Flow<String> = context.dataStore.data.map { it[SWIPE_RIGHT_ACTION_KEY] ?: "archive" }

    suspend fun setSwipeRightAction(action: String) {
        context.dataStore.edit { it[SWIPE_RIGHT_ACTION_KEY] = action }
    }

    val swipeLeftAction: Flow<String> = context.dataStore.data.map { it[SWIPE_LEFT_ACTION_KEY] ?: "delete" }

    suspend fun setSwipeLeftAction(action: String) {
        context.dataStore.edit { it[SWIPE_LEFT_ACTION_KEY] = action }
    }

    // --- WhatsApp settings ---
    val whatsappEnabled: Flow<Boolean> = context.dataStore.data.map { it[WHATSAPP_ENABLED_KEY] ?: false }

    suspend fun setWhatsappEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WHATSAPP_ENABLED_KEY] = enabled }
    }
}
