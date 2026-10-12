package com.cubeslauncher.phone

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "phone_prefs")

class PhonePreferencesManager(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val PHONE_CALLER_ID_KEY = stringPreferencesKey("phone_caller_id")
        private val PHONE_CALL_WAITING_KEY = booleanPreferencesKey("phone_call_waiting")
        private val PHONE_VOICEMAIL_NUMBER_KEY = stringPreferencesKey("phone_voicemail_number")
        private val PHONE_INTERNATIONAL_ASSIST_KEY = booleanPreferencesKey("phone_international_assist")
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
}
