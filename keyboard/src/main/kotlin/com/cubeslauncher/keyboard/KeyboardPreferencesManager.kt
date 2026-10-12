package com.cubeslauncher.keyboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "keyboard_prefs")

class KeyboardPreferencesManager(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val KEYBOARD_SOUND_KEY = booleanPreferencesKey("keyboard_sound")
        private val KEYBOARD_VIBRATION_KEY = booleanPreferencesKey("keyboard_vibration")
        private val KEYBOARD_HEIGHT_KEY = intPreferencesKey("keyboard_height")
        private val KEYBOARD_AUTO_CORRECT_KEY = booleanPreferencesKey("keyboard_auto_correct")
        private val KEYBOARD_AUTO_CAPITALIZE_KEY = booleanPreferencesKey("keyboard_auto_capitalize")
        private val KEYBOARD_DOUBLE_SPACE_PERIOD_KEY = booleanPreferencesKey("keyboard_double_space_period")
        private val KEYBOARD_SHOW_SUGGESTIONS_KEY = booleanPreferencesKey("keyboard_show_suggestions")
        private val KEYBOARD_GLIDE_TYPING_KEY = booleanPreferencesKey("keyboard_glide_typing")
        private val KEYBOARD_GLIDE_TRAIL_KEY = booleanPreferencesKey("keyboard_glide_trail")
        private val KEYBOARD_NUMBER_ROW_KEY = booleanPreferencesKey("keyboard_number_row")
        private val KEYBOARD_VOICE_INPUT_KEY = booleanPreferencesKey("keyboard_voice_input")
        private val KEYBOARD_CLIPBOARD_KEY = booleanPreferencesKey("keyboard_clipboard")
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

    // --- Keyboard settings ---
    val keyboardSound: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_SOUND_KEY] ?: true }

    suspend fun setKeyboardSound(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_SOUND_KEY] = enabled }
    }

    val keyboardVibration: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_VIBRATION_KEY] ?: true }

    suspend fun setKeyboardVibration(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_VIBRATION_KEY] = enabled }
    }

    val keyboardHeight: Flow<Int> = context.dataStore.data.map { it[KEYBOARD_HEIGHT_KEY] ?: 1 }

    suspend fun setKeyboardHeight(height: Int) {
        context.dataStore.edit { it[KEYBOARD_HEIGHT_KEY] = height.coerceIn(0, 2) }
    }

    val keyboardAutoCorrect: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_AUTO_CORRECT_KEY] ?: true }

    suspend fun setKeyboardAutoCorrect(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_AUTO_CORRECT_KEY] = enabled }
    }

    val keyboardAutoCapitalize: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_AUTO_CAPITALIZE_KEY] ?: true }

    suspend fun setKeyboardAutoCapitalize(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_AUTO_CAPITALIZE_KEY] = enabled }
    }

    val keyboardDoubleSpacePeriod: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_DOUBLE_SPACE_PERIOD_KEY] ?: true }

    suspend fun setKeyboardDoubleSpacePeriod(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_DOUBLE_SPACE_PERIOD_KEY] = enabled }
    }

    val keyboardShowSuggestions: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_SHOW_SUGGESTIONS_KEY] ?: true }

    suspend fun setKeyboardShowSuggestions(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_SHOW_SUGGESTIONS_KEY] = enabled }
    }

    val keyboardGlideTyping: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_GLIDE_TYPING_KEY] ?: true }

    suspend fun setKeyboardGlideTyping(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_GLIDE_TYPING_KEY] = enabled }
    }

    val keyboardGlideTrail: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_GLIDE_TRAIL_KEY] ?: true }

    suspend fun setKeyboardGlideTrail(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_GLIDE_TRAIL_KEY] = enabled }
    }

    val keyboardNumberRow: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_NUMBER_ROW_KEY] ?: false }

    suspend fun setKeyboardNumberRow(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_NUMBER_ROW_KEY] = enabled }
    }

    val keyboardVoiceInput: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_VOICE_INPUT_KEY] ?: true }

    suspend fun setKeyboardVoiceInput(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_VOICE_INPUT_KEY] = enabled }
    }

    val keyboardClipboard: Flow<Boolean> = context.dataStore.data.map { it[KEYBOARD_CLIPBOARD_KEY] ?: true }

    suspend fun setKeyboardClipboard(enabled: Boolean) {
        context.dataStore.edit { it[KEYBOARD_CLIPBOARD_KEY] = enabled }
    }
}
