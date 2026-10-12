package com.cubeslauncher.contacts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "contacts_prefs")

class ContactsPreferencesManager(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val CONTACTS_SORT_ORDER_KEY = stringPreferencesKey("contacts_sort_order")
        private val CONTACTS_DISPLAY_FORMAT_KEY = stringPreferencesKey("contacts_display_format")
        private val CONTACTS_HIDE_NO_PHONE_KEY = booleanPreferencesKey("contacts_hide_no_phone")
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
}
